package com.ptiters.marketplace.report;

import com.ptiters.marketplace.exception.ApiException;
import com.ptiters.marketplace.integration.MediaStorage;
import com.ptiters.marketplace.notification.NotificationWriter;
import com.ptiters.marketplace.order.Order;
import com.ptiters.marketplace.order.OrderRepository;
import com.ptiters.marketplace.product.Product;
import com.ptiters.marketplace.product.ProductRepository;
import com.ptiters.marketplace.product.ProductStatus;
import com.ptiters.marketplace.realtime.MediaValidator;
import com.ptiters.marketplace.report.ReportSummaries.ReportEvidenceMetadata;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserRepository;
import com.ptiters.marketplace.user.UserRole;
import com.ptiters.marketplace.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Tạo báo cáo vi phạm + tải bằng chứng. Tương ứng phần user-facing của {@code reports/service.py}. */
@Service
@RequiredArgsConstructor
public class ReportService {

    static final int MAX_IMAGE_EVIDENCE = 5;
    static final int MAX_VIDEO_EVIDENCE = 1;
    static final double MAX_VIDEO_DURATION_SECONDS = 60.0;
    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportQueryRepository repo;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final NotificationWriter notificationWriter;
    private final MediaStorage mediaStorage;
    private final MediaValidator mediaValidator;
    private final TransactionTemplate tx;

    @Transactional
    public ReportResponse createReport(User current, ReportCreateRequest req) {
        Long resolvedTargetId = req.targetId();

        switch (req.targetType()) {
            case USER -> {
                User target = userRepository.findByPublicId(req.targetPublicId())
                        .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                        .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng."));
                resolvedTargetId = target.getUserId();
                if (resolvedTargetId.equals(current.getUserId())) {
                    throw ApiException.badRequest("Không thể tự báo cáo chính mình.");
                }
            }
            case PRODUCT -> {
                Product product = productRepository.findById(req.targetId())
                        .filter(p -> !Boolean.TRUE.equals(p.getIsDeleted()))
                        .orElseThrow(() -> ApiException.notFound("Product not found."));
                Long sellerId = product.getSeller().getUserId();
                if (product.getStatus() == ProductStatus.HIDDEN) {
                    boolean authorized = current.getUserId().equals(sellerId) || current.getRole() == UserRole.ADMIN;
                    if (!authorized) {
                        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                        authorized = repo.findLatestEndedAuctionWithActiveWinnerRight(product.getProductId(), now)
                                .map(a -> current.getUserId().equals(a.getWinnerId())).orElse(false);
                    }
                    if (!authorized) {
                        throw ApiException.notFound("Product not found.");
                    }
                }
                if (current.getUserId().equals(sellerId)) {
                    throw ApiException.badRequest("Không thể báo cáo sản phẩm của chính mình.");
                }
            }
            case ORDER -> {
                Order order = orderRepository.findById(req.targetId())
                        .filter(o -> o.getBuyerId().equals(current.getUserId()) || o.getSellerId().equals(current.getUserId()))
                        .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng."));
                resolvedTargetId = order.getOrderId();
            }
        }

        if (repo.hasActiveReport(current.getUserId(), req.targetType(), resolvedTargetId)) {
            throw ApiException.conflict("Bạn đã gửi báo cáo cho đối tượng này và đang được xử lý.");
        }

        Report report = Report.builder()
                .reporterId(current.getUserId())
                .targetType(req.targetType())
                .targetId(resolvedTargetId)
                .reason(req.reason())
                .description(req.description())
                .status(ReportStatus.OPEN)
                .build();
        report = reportRepository.saveAndFlush(report);

        notificationWriter.notifyAdmins(
                "Có báo cáo vi phạm mới",
                "Một báo cáo " + report.getTargetType().name() + " mới đang chờ xử lý.",
                "ADMIN_REPORT",
                report.getReportId());

        return new ReportResponse(report.getReportId(), report.getTargetType().name(), report.getTargetId(),
                report.getReason().name(), report.getDescription(), report.getStatus().name(), report.getCreatedAt());
    }

    /**
     * Tải bằng chứng 2 pha: (1) kiểm tra quyền + validate cục bộ toàn bộ tệp, (2) upload ra storage KHÔNG giữ khoá DB,
     * (3) transaction khoá Report FOR UPDATE, kiểm tra lại giới hạn rồi ghi DB; lỗi → bù trừ xoá asset đã upload.
     */
    public ReportEvidenceUploadResponse uploadEvidence(User current, Long reportId, List<MultipartFile> files) {
        // Pha A: tiền kiểm tra (không khoá)
        Counts existing = tx.execute(status -> {
            Report report = reportRepository.findById(reportId)
                    .filter(r -> r.getReporterId().equals(current.getUserId()))
                    .orElseThrow(() -> ApiException.notFound("Báo cáo không tồn tại."));
            assertOpen(report);
            return countAttachments(reportId);
        });

        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Vui lòng chọn ít nhất một tệp minh chứng.");
        }

        // Pha B: validate cục bộ TOÀN BỘ tệp trước khi upload bất kỳ tệp nào
        List<ValidatedFile> validated = new ArrayList<>();
        int newImages = 0;
        int newVideos = 0;
        for (MultipartFile file : files) {
            byte[] bytes;
            try {
                bytes = file.getBytes();
            } catch (IOException e) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Không đọc được tệp tải lên.");
            }
            String name = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                    ? "evidence" : file.getOriginalFilename();
            MediaValidator.MediaEvidence evidence = mediaValidator.validateMediaEvidenceBytes(name, bytes);
            boolean video = "VIDEO".equals(evidence.mediaType());
            if (video) {
                newVideos++;
            } else {
                newImages++;
            }
            validated.add(new ValidatedFile(bytes, name, evidence.mimeType(), video));
        }
        if (existing.images() + newImages > MAX_IMAGE_EVIDENCE) {
            throw new ApiException(existing.images() > 0 ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY,
                    "Tổng số ảnh minh chứng vượt quá giới hạn tối đa (" + MAX_IMAGE_EVIDENCE
                            + " ảnh). Hiện báo cáo đã có " + existing.images() + " ảnh.");
        }
        if (existing.videos() + newVideos > MAX_VIDEO_EVIDENCE) {
            throw new ApiException(existing.videos() > 0 ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY,
                    "Tổng số video minh chứng vượt quá giới hạn tối đa (" + MAX_VIDEO_EVIDENCE
                            + " video). Hiện báo cáo đã có " + existing.videos() + " video.");
        }

        // Pha C: upload ra storage, theo dõi mọi asset đã tạo
        List<UploadedRecord> uploaded = new ArrayList<>();
        try {
            for (ValidatedFile f : validated) {
                String resourceType = f.video() ? "video" : "image";
                MediaStorage.UploadedAsset asset = mediaStorage.uploadReportEvidence(f.bytes(), f.name(), reportId, resourceType);
                uploaded.add(new UploadedRecord(f, asset));
                if (f.video() && asset.durationSeconds() != null && asset.durationSeconds() > MAX_VIDEO_DURATION_SECONDS) {
                    cleanup(uploaded);
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Thời lượng video '" + f.name() + "' vượt quá giới hạn tối đa ("
                                    + (int) MAX_VIDEO_DURATION_SECONDS + " giây). Thời lượng thực tế: "
                                    + Math.round(asset.durationSeconds() * 10) / 10.0 + " giây.");
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            cleanup(uploaded);
            log.error("Media storage upload error during evidence upload for report {}: {}", reportId, e.toString());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Lỗi khi tải tệp lên dịch vụ lưu trữ.");
        }

        // Pha D: ghi DB dưới khoá Report FOR UPDATE, kiểm tra lại, commit một lần
        try {
            List<ReportEvidenceMetadata> saved = tx.execute(status -> {
                Report locked = repo.lockReport(reportId)
                        .filter(r -> r.getReporterId().equals(current.getUserId()))
                        .orElseThrow(() -> ApiException.notFound("Báo cáo không tồn tại."));
                assertOpen(locked);
                Counts now = countAttachments(reportId);
                if (now.images() + validatedImages(validated) > MAX_IMAGE_EVIDENCE) {
                    throw ApiException.conflict("Tổng số ảnh minh chứng vượt quá giới hạn tối đa (" + MAX_IMAGE_EVIDENCE + " ảnh).");
                }
                if (now.videos() + validatedVideos(validated) > MAX_VIDEO_EVIDENCE) {
                    throw ApiException.conflict("Tổng số video minh chứng vượt quá giới hạn tối đa (" + MAX_VIDEO_EVIDENCE + " video).");
                }
                List<ReportEvidenceMetadata> result = new ArrayList<>();
                for (UploadedRecord rec : uploaded) {
                    ReportAttachment att = ReportAttachment.builder()
                            .report(locked)
                            .url(rec.asset().secureUrl())
                            .storageKey(rec.asset().publicId())
                            .mimeType(rec.file().mimeType())
                            .originalName(truncate(rec.file().name(), 255))
                            .sizeBytes((long) rec.file().bytes().length)
                            .build();
                    repo.persist(att);
                    repo.flush();
                    result.add(new ReportEvidenceMetadata(att.getAttachmentId(), att.getOriginalName(), att.getMimeType(),
                            att.getSizeBytes(), att.getCreatedAt(), rec.file().video() ? "VIDEO" : "IMAGE"));
                }
                return result;
            });
            return new ReportEvidenceUploadResponse(reportId, saved);
        } catch (ApiException e) {
            cleanup(uploaded);
            throw e;
        } catch (RuntimeException e) {
            cleanup(uploaded);
            log.error("Failed to persist report attachments for report {}: {}", reportId, e.toString());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi khi lưu thông tin tệp minh chứng.");
        }
    }

    // ------------------------------------------------------------------ helpers

    private void assertOpen(Report report) {
        if (report.getStatus() == ReportStatus.RESOLVED || report.getStatus() == ReportStatus.REJECTED) {
            throw ApiException.conflict("Không thể thêm minh chứng cho báo cáo đã ở trạng thái kết thúc '"
                    + report.getStatus().name() + "'.");
        }
    }

    private Counts countAttachments(Long reportId) {
        int images = 0;
        int videos = 0;
        for (ReportAttachment a : repo.listAttachments(reportId)) {
            if (a.getMimeType() != null && a.getMimeType().startsWith("video/")) {
                videos++;
            } else {
                images++;
            }
        }
        return new Counts(images, videos);
    }

    private static int validatedImages(List<ValidatedFile> files) {
        return (int) files.stream().filter(f -> !f.video()).count();
    }

    private static int validatedVideos(List<ValidatedFile> files) {
        return (int) files.stream().filter(ValidatedFile::video).count();
    }

    private void cleanup(List<UploadedRecord> uploaded) {
        for (UploadedRecord rec : uploaded) {
            try {
                mediaStorage.destroyAsset(rec.asset().publicId(), rec.asset().resourceType());
            } catch (RuntimeException e) {
                log.warn("Compensation cleanup failed for asset {}: {}", rec.asset().publicId(), e.toString());
            }
        }
        uploaded.clear();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private record Counts(int images, int videos) {}

    private record ValidatedFile(byte[] bytes, String name, String mimeType, boolean video) {}

    private record UploadedRecord(ValidatedFile file, MediaStorage.UploadedAsset asset) {}
}
