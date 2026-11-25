package com.ptiters.marketplace.communitymoment;

import com.ptiters.marketplace.exception.ApiException;
import com.ptiters.marketplace.integration.MediaStorage;
import com.ptiters.marketplace.integration.MediaStorageException;
import com.ptiters.marketplace.integration.MediaStorageNotConfiguredException;
import com.ptiters.marketplace.notification.NotificationType;
import com.ptiters.marketplace.notification.NotificationWriter;
import com.ptiters.marketplace.realtime.MediaValidator;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserRepository;
import com.ptiters.marketplace.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityMomentService {

    private static final int MAX_HERO_MOMENTS = 5;
    private static final int MAX_NOTE_CODEPOINTS = 80;

    private final CommunityMomentRepository communityMomentRepository;
    private final CommunityMomentQueryRepository queryRepository;
    private final UserRepository userRepository;
    private final MediaValidator mediaValidator;
    private final MediaStorage mediaStorage;
    private final NotificationWriter notificationWriter;
    private final TransactionTemplate transactionTemplate;

    public record CursorTuple(LocalDateTime createdAt, Long momentId) {
    }

    public static String validateCommunityNote(String rawNote) {
        if (rawNote == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Lời nhắn là bắt buộc.");
        }
        String normalized = Normalizer.normalize(rawNote, Normalizer.Form.NFC);
        if (normalized.indexOf('\n') >= 0 || normalized.indexOf('\r') >= 0) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Lời nhắn chỉ được chứa một dòng duy nhất, không chứa ký tự xuống dòng."
            );
        }
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (ch == '\u000B' || ch == '\u000C' || ch == '\u001C' || ch == '\u001D'
                    || ch == '\u001E' || ch == '\u0085' || ch == '\u2028' || ch == '\u2029') {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Lời nhắn chỉ được chứa một dòng duy nhất."
                );
            }
        }
        String trimmed = normalized.strip();
        if (trimmed.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Lời nhắn không được để trống.");
        }
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_NOTE_CODEPOINTS) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Lời nhắn không được vượt quá 80 ký tự."
            );
        }
        return trimmed;
    }

    public static CursorTuple decodeCursor(String cursorStr) {
        if (cursorStr == null || cursorStr.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursorStr.trim());
            String raw = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|");
            if (parts.length != 2) {
                throw ApiException.badRequest("Invalid pagination cursor.");
            }
            LocalDateTime createdAt = LocalDateTime.parse(parts[0]);
            Long momentId = Long.parseLong(parts[1]);
            return new CursorTuple(createdAt, momentId);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("Invalid pagination cursor.");
        }
    }

    public CommunityMomentSubmissionResponse submitCommunityMoment(
            User uploader,
            MultipartFile file,
            String note
    ) {
        if (uploader.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("Tài khoản của bạn không ở trạng thái hoạt động.");
        }

        String validatedNote = validateCommunityNote(note);

        if (file == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Tệp ảnh khoảnh khắc là bắt buộc.");
        }

        boolean alreadyPending = Boolean.TRUE.equals(
                transactionTemplate.execute(status -> queryRepository.hasPendingByUploaderId(uploader.getUserId()))
        );
        if (alreadyPending) {
            throw new CommunityMomentPendingException("Bạn đã có một ảnh đang chờ duyệt.");
        }

        String originalFilename = (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank())
                ? "community_moment"
                : file.getOriginalFilename();
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Không thể đọc tệp ảnh tải lên.");
        }

        String canonicalMime = mediaValidator.validateImageBytes(originalFilename, fileBytes);
        if (!MediaValidator.ALLOWED_COMMUNITY_IMAGE_MIMES.contains(canonicalMime)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Định dạng tệp '" + originalFilename + "' không được hỗ trợ. Chỉ chấp nhận ảnh (JPEG, PNG, WebP)."
            );
        }

        MediaStorage.UploadedAsset uploaded;
        try {
            uploaded = mediaStorage.uploadCommunityMomentImage(fileBytes, originalFilename, uploader.getUserId());
        } catch (MediaStorageNotConfiguredException | MediaStorageException e) {
            log.error("Media storage upload failed for user {}: {}", uploader.getUserId(), e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ lưu trữ ảnh tạm thời không khả dụng.");
        }

        try {
            CommunityMoment saved = transactionTemplate.execute(txStatus -> {
                CommunityMoment moment = CommunityMoment.builder()
                        .uploaderId(uploader.getUserId())
                        .imageUrl(uploaded.secureUrl())
                        .storageKey(uploaded.publicId())
                        .mimeType(canonicalMime)
                        .sizeBytes((long) fileBytes.length)
                        .note(validatedNote)
                        .status(CommunityMomentStatus.PENDING)
                        .build();

                CommunityMoment created = communityMomentRepository.saveAndFlush(moment);

                notificationWriter.notifyAdmins(
                        "Có ảnh cộng đồng mới chờ duyệt",
                        "Một khoảnh khắc mới đang chờ quản trị viên duyệt.",
                        "ADMIN_COMMUNITY",
                        created.getMomentId()
                );

                return created;
            });

            if (saved == null) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu ảnh khoảnh khắc. Vui lòng thử lại.");
            }

            return new CommunityMomentSubmissionResponse(
                    saved.getMomentId(),
                    saved.getStatus().name(),
                    saved.getCreatedAt()
            );
        } catch (DataIntegrityViolationException e) {
            safeDestroyImage(uploaded.publicId());
            if (isPendingUniqueConstraintViolation(e)) {
                throw new CommunityMomentPendingException("Bạn đã có một ảnh đang chờ duyệt.");
            }
            log.error("DB integrity error on community moment insert: {}", e.getMessage());
            throw ApiException.badRequest("Lỗi ràng buộc dữ liệu khi lưu ảnh.");
        } catch (RuntimeException e) {
            safeDestroyImage(uploaded.publicId());
            if (e instanceof ApiException || e instanceof CommunityMomentPendingException) {
                throw e;
            }
            log.error("Unexpected error on community moment insert", e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu ảnh khoảnh khắc. Vui lòng thử lại.");
        }
    }

    @Transactional(readOnly = true)
    public PublicHeroResponse getPublicHeroMoments() {
        List<CommunityMoment> moments = queryRepository.findLatestApprovedForHero(MAX_HERO_MOMENTS);
        List<PublicCommunityMomentItem> items = moments.stream()
                .map(m -> {
                    String heroUrl = mediaStorage.heroImageUrl(m.getImageUrl());
                    return new PublicCommunityMomentItem(
                            m.getMomentId(),
                            (heroUrl != null && !heroUrl.isEmpty()) ? heroUrl : m.getImageUrl(),
                            m.getNote(),
                            m.getReviewedAt()
                    );
                })
                .toList();
        return new PublicHeroResponse(items);
    }

    @Transactional(readOnly = true)
    public AdminCommunityMomentCursorPage listAdminCommunityMoments(int limit, String cursor, String statusRaw) {
        CommunityMomentStatus statusFilter = parseStatusFilter(statusRaw);
        CursorTuple cursorTuple = decodeCursor(cursor);
        LocalDateTime cursorTime = cursorTuple != null ? cursorTuple.createdAt() : null;
        Long cursorId = cursorTuple != null ? cursorTuple.momentId() : null;

        CommunityMomentQueryRepository.PaginatedMoments page =
                queryRepository.listAdminMomentsCursor(statusFilter, cursorTime, cursorId, limit);

        List<AdminCommunityMomentItem> dtoItems = page.items().stream()
                .map(this::toAdminItem)
                .toList();

        return new AdminCommunityMomentCursorPage(dtoItems, page.nextCursor(), page.hasMore());
    }

    @Transactional(readOnly = true)
    public AdminCommunityMomentItem getAdminCommunityMomentDetail(Long momentId) {
        CommunityMoment moment = queryRepository.findByIdWithRelations(momentId)
                .orElseThrow(() -> ApiException.notFound("Khoảnh khắc cộng đồng không tồn tại."));
        return toAdminItem(moment);
    }

    public AdminCommunityMomentItem approveCommunityMoment(Long momentId, User admin) {
        List<String> prunedStorageKeys = new ArrayList<>();

        AdminCommunityMomentItem result = transactionTemplate.execute(txStatus -> {
            queryRepository.acquireHeroAdvisoryLock();

            CommunityMoment moment = queryRepository.findByIdForUpdate(momentId)
                    .orElseThrow(() -> ApiException.notFound("Khoảnh khắc cộng đồng không tồn tại."));

            if (moment.getStatus() != CommunityMomentStatus.PENDING) {
                throw ApiException.conflict(
                        "Khoảnh khắc này đã được xử lý trước đó và không còn ở trạng thái chờ duyệt."
                );
            }

            LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
            moment.setStatus(CommunityMomentStatus.APPROVED);
            moment.setReviewedBy(admin.getUserId());
            moment.setReviewedAt(nowUtc);
            communityMomentRepository.saveAndFlush(moment);

            notificationWriter.create(
                    moment.getUploaderId(),
                    NotificationType.SYSTEM,
                    "Khoảnh khắc của bạn đã được duyệt",
                    "Khoảnh khắc của bạn đã được duyệt và đang hiển thị trên trang chủ.",
                    "COMMUNITY_MOMENT",
                    moment.getMomentId()
            );

            List<CommunityMoment> allApproved = queryRepository.findAllApprovedOrderByReviewedDescForUpdate();
            if (allApproved.size() > MAX_HERO_MOMENTS) {
                List<CommunityMoment> excess = allApproved.subList(MAX_HERO_MOMENTS, allApproved.size());
                for (CommunityMoment ex : excess) {
                    if (ex.getStorageKey() != null && !ex.getStorageKey().isBlank()) {
                        prunedStorageKeys.add(ex.getStorageKey());
                    }
                    communityMomentRepository.delete(ex);
                }
                communityMomentRepository.flush();
            }

            User uploader = userRepository.findById(moment.getUploaderId()).orElse(null);
            return toAdminItem(moment, uploader, admin);
        });

        for (String prunedKey : prunedStorageKeys) {
            safeDestroyImage(prunedKey);
        }

        return result;
    }

    public CommunityMomentActionResponse rejectCommunityMoment(Long momentId, User admin) {
        String storageKey = transactionTemplate.execute(txStatus -> {
            CommunityMoment moment = queryRepository.findByIdForUpdate(momentId)
                    .orElseThrow(() -> ApiException.notFound("Khoảnh khắc cộng đồng không tồn tại."));

            if (moment.getStatus() != CommunityMomentStatus.PENDING) {
                throw ApiException.conflict(
                        "Khoảnh khắc này đã được xử lý trước đó và không còn ở trạng thái chờ duyệt."
                );
            }

            Long uploaderId = moment.getUploaderId();
            Long targetMomentId = moment.getMomentId();
            String key = moment.getStorageKey();

            notificationWriter.create(
                    uploaderId,
                    NotificationType.SYSTEM,
                    "Khoảnh khắc chưa được duyệt",
                    "Khoảnh khắc bạn gửi chưa phù hợp với tiêu chuẩn cộng đồng Chợ PTITers.",
                    "COMMUNITY_REJECTED",
                    targetMomentId
            );

            communityMomentRepository.delete(moment);
            communityMomentRepository.flush();
            return key;
        });

        safeDestroyImage(storageKey);

        return new CommunityMomentActionResponse(
                momentId,
                "REJECTED",
                "Đã từ chối và xóa khoảnh khắc vĩnh viễn."
        );
    }

    public CommunityMomentActionResponse deleteCommunityMoment(Long momentId, User admin) {
        String storageKey = transactionTemplate.execute(txStatus -> {
            queryRepository.acquireHeroAdvisoryLock();

            CommunityMoment moment = queryRepository.findByIdForUpdate(momentId)
                    .orElseThrow(() -> ApiException.notFound("Khoảnh khắc cộng đồng không tồn tại."));

            if (moment.getStatus() != CommunityMomentStatus.APPROVED) {
                throw ApiException.conflict("Chỉ có thể xóa ảnh đang ở trạng thái đã duyệt trên Hero.");
            }

            String key = moment.getStorageKey();
            communityMomentRepository.delete(moment);
            communityMomentRepository.flush();
            return key;
        });

        safeDestroyImage(storageKey);

        return new CommunityMomentActionResponse(
                momentId,
                "DELETED",
                "Đã xóa khoảnh khắc khỏi Hero vĩnh viễn."
        );
    }

    private AdminCommunityMomentItem toAdminItem(CommunityMoment m) {
        return toAdminItem(m, m.getUploader(), m.getReviewer());
    }

    private AdminCommunityMomentItem toAdminItem(CommunityMoment m, User uploader, User reviewer) {
        AdminCommunityMomentUploader uploaderDto = uploader != null
                ? new AdminCommunityMomentUploader(
                        uploader.getUserId(),
                        uploader.getFullName(),
                        uploader.getAvatarUrl())
                : new AdminCommunityMomentUploader(m.getUploaderId(), "", null);

        AdminCommunityMomentReviewer reviewerDto = reviewer != null
                ? new AdminCommunityMomentReviewer(reviewer.getUserId(), reviewer.getFullName())
                : null;

        return new AdminCommunityMomentItem(
                m.getMomentId(),
                m.getImageUrl(),
                mediaStorage.thumbnailUrl(m.getImageUrl()),
                mediaStorage.detailImageUrl(m.getImageUrl()),
                m.getNote(),
                m.getStatus().name(),
                m.getCreatedAt(),
                m.getReviewedAt(),
                uploaderDto,
                reviewerDto
        );
    }

    private static CommunityMomentStatus parseStatusFilter(String statusRaw) {
        if (statusRaw == null || statusRaw.isBlank()) {
            return CommunityMomentStatus.PENDING;
        }
        try {
            return CommunityMomentStatus.valueOf(statusRaw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Trạng thái lọc không hợp lệ.");
        }
    }

    private static boolean isPendingUniqueConstraintViolation(Throwable throwable) {
        Throwable curr = throwable;
        while (curr != null) {
            String msg = curr.getMessage();
            if (msg != null && msg.contains("uq_community_moments_user_pending")) {
                return true;
            }
            curr = curr.getCause();
        }
        return false;
    }

    private void safeDestroyImage(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            mediaStorage.destroyAsset(storageKey, "image");
        } catch (RuntimeException e) {
            log.warn("Best-effort cleanup failed for community moment asset {}: {}", storageKey, e.getMessage());
        }
    }
}
