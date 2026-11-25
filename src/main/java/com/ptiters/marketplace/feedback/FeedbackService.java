package com.ptiters.marketplace.feedback;

import com.ptiters.marketplace.exception.ApiException;
import com.ptiters.marketplace.integration.LocalMediaStorage;
import com.ptiters.marketplace.integration.MediaStorage;
import com.ptiters.marketplace.notification.NotificationType;
import com.ptiters.marketplace.notification.NotificationWriter;
import com.ptiters.marketplace.realtime.MediaValidator;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserRepository;
import com.ptiters.marketplace.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackService {

    public static final int MAX_FEEDBACK_IMAGES = 3;
    public static final int MAX_FEEDBACK_VIDEOS = 1;
    public static final int MAX_FEEDBACK_ATTACHMENTS = 4;
    private static final String DEFAULT_QR_URL = "/support/nguyen-khac-thinh-vietqr.png";
    private static final String DEFAULT_SUPPORT_MESSAGE = "Quan trọng là tấm lòng 💛";
    private static final String CREATOR_TITLE = "Người phát triển Chợ PTITers";

    private final SiteFeedbackRepository siteFeedbackRepository;
    private final SiteFeedbackAttachmentRepository siteFeedbackAttachmentRepository;
    private final SupportContributionRepository supportContributionRepository;
    private final SupportClaimRepository supportClaimRepository;
    private final FeedbackQueryRepository feedbackQueryRepository;
    private final UserRepository userRepository;
    private final NotificationWriter notificationWriter;
    private final MediaValidator mediaValidator;
    private final MediaStorage mediaStorage;
    private final TransactionTemplate transactionTemplate;
    private final Environment environment;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private record ValidatedFile(
            byte[] bytes,
            String filename,
            String mimeType,
            FeedbackAttachmentKind kind,
            long sizeBytes
    ) {}

    private record AssetRef(String storageKey, String resourceType) {}

    private record FeedbackCursor(LocalDateTime createdAt, Long feedbackId) {}

    private record SupportCursor(OffsetDateTime timestamp, Long id) {}

    public record EvidenceStreamResult(int statusCode, HttpHeaders headers, Resource body) {}

    // =========================================================================
    // CONTENT & CURSOR HELPERS
    // =========================================================================

    public static String sanitizeFeedbackContent(String raw) {
        if (raw == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Nội dung phản hồi phải có ít nhất 5 ký tự.");
        }
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFC);
        String trimmed = normalized.trim();
        if (trimmed.length() < 5) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Nội dung phản hồi phải có ít nhất 5 ký tự.");
        }
        if (trimmed.length() > 500) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Nội dung phản hồi không được vượt quá 500 ký tự.");
        }
        for (int i = 0; i < trimmed.length(); ) {
            int cp = trimmed.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\r' || cp == '\n' || cp == '\t') {
                continue;
            }
            int type = Character.getType(cp);
            if (type == Character.CONTROL
                    || type == Character.FORMAT
                    || type == Character.PRIVATE_USE
                    || type == Character.SURROGATE
                    || type == Character.UNASSIGNED) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Nội dung phản hồi chứa ký tự điều khiển không hợp lệ.");
            }
        }
        return trimmed;
    }

    private static String encodeFeedbackCursor(LocalDateTime createdAt, Long feedbackId) {
        String payload = createdAt.toString() + "|" + feedbackId;
        return Base64.getUrlEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static FeedbackCursor decodeFeedbackCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid cursor format");
            }
            LocalDateTime dt = LocalDateTime.parse(parts[0]);
            Long id = Long.parseLong(parts[1]);
            return new FeedbackCursor(dt, id);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Con trỏ phân trang không hợp lệ.");
        }
    }

    private static String encodeSupportCursor(OffsetDateTime timestamp, Long id) {
        String payload = timestamp.toString() + "|" + id;
        return Base64.getUrlEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static SupportCursor decodeSupportCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid cursor format");
            }
            OffsetDateTime dt = OffsetDateTime.parse(parts[0]);
            Long id = Long.parseLong(parts[1]);
            return new SupportCursor(dt, id);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Con trỏ phân trang không hợp lệ.");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, 50));
    }

    // =========================================================================
    // SITE FEEDBACK (USER & PUBLIC)
    // =========================================================================

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FeedbackCreateResponse submitFeedback(
            User currentUser,
            String content,
            List<MultipartFile> images,
            MultipartFile video
    ) {
        if (currentUser.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("Tài khoản của bạn không có quyền gửi phản hồi.");
        }

        String cleanedContent = sanitizeFeedbackContent(content);

        List<MultipartFile> imageFiles = images == null ? List.of() : images.stream()
                .filter(f -> f != null && f.getOriginalFilename() != null && !f.getOriginalFilename().isEmpty())
                .toList();
        List<MultipartFile> videoFiles = (video != null && video.getOriginalFilename() != null
                && !video.getOriginalFilename().isEmpty())
                ? List.of(video)
                : List.of();

        if (imageFiles.size() > MAX_FEEDBACK_IMAGES) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Số lượng ảnh minh chứng tối đa là " + MAX_FEEDBACK_IMAGES + ".");
        }
        if (videoFiles.size() > MAX_FEEDBACK_VIDEOS) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Số lượng video minh chứng tối đa là " + MAX_FEEDBACK_VIDEOS + ".");
        }
        if (imageFiles.size() + videoFiles.size() > MAX_FEEDBACK_ATTACHMENTS) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Tổng số tệp đính kèm tối đa là " + MAX_FEEDBACK_ATTACHMENTS + ".");
        }

        List<ValidatedFile> validatedFiles = new ArrayList<>();
        for (MultipartFile img : imageFiles) {
            String filename = img.getOriginalFilename() != null ? img.getOriginalFilename() : "image";
            byte[] bytes = readMultipartBytes(img, filename);
            MediaValidator.MediaEvidence evidence = mediaValidator.validateMediaEvidenceBytes(filename, bytes);
            if (!"IMAGE".equals(evidence.mediaType())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Tệp '" + filename + "' không phải là ảnh hợp lệ.");
            }
            validatedFiles.add(new ValidatedFile(bytes, filename, evidence.mimeType(),
                    FeedbackAttachmentKind.IMAGE, bytes.length));
        }

        for (MultipartFile vid : videoFiles) {
            String filename = vid.getOriginalFilename() != null ? vid.getOriginalFilename() : "video";
            byte[] bytes = readMultipartBytes(vid, filename);
            MediaValidator.MediaEvidence evidence = mediaValidator.validateMediaEvidenceBytes(filename, bytes);
            if (!"VIDEO".equals(evidence.mediaType())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Tệp '" + filename + "' không phải là video hợp lệ.");
            }
            validatedFiles.add(new ValidatedFile(bytes, filename, evidence.mimeType(),
                    FeedbackAttachmentKind.VIDEO, bytes.length));
        }

        List<AssetRef> uploadedAssets = new ArrayList<>();
        List<SiteFeedbackAttachment> preparedAttachments = new ArrayList<>();

        try {
            for (ValidatedFile item : validatedFiles) {
                String resType = item.kind() == FeedbackAttachmentKind.VIDEO ? "video" : "image";
                MediaStorage.UploadedAsset uploaded = mediaStorage.uploadFeedbackAttachment(
                        item.bytes(), item.filename(), resType
                );
                uploadedAssets.add(new AssetRef(uploaded.publicId(), resType));

                Long durationMs = null;
                if (item.kind() == FeedbackAttachmentKind.VIDEO && uploaded.durationSeconds() != null) {
                    if (uploaded.durationSeconds() > MediaValidator.MAX_VIDEO_DURATION_SECONDS) {
                        destroyAssetsBestEffort(uploadedAssets);
                        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                                "Thời lượng video vượt quá giới hạn tối đa ("
                                        + (int) MediaValidator.MAX_VIDEO_DURATION_SECONDS + " giây).");
                    }
                    durationMs = Math.round(uploaded.durationSeconds() * 1000.0);
                }

                preparedAttachments.add(SiteFeedbackAttachment.builder()
                        .kind(item.kind())
                        .mediaUrl(uploaded.secureUrl())
                        .storageKey(uploaded.publicId())
                        .mimeType(item.mimeType())
                        .sizeBytes(item.sizeBytes())
                        .durationMs(durationMs)
                        .build());
            }
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            destroyAssetsBestEffort(uploadedAssets);
            log.error("Media upload error during feedback creation: {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Lỗi khi tải tệp lên dịch vụ lưu trữ.");
        }

        try {
            FeedbackCreateResponse response = transactionTemplate.execute(status -> {
                SiteFeedback feedback = SiteFeedback.builder()
                        .userId(currentUser.getUserId())
                        .content(cleanedContent)
                        .build();
                SiteFeedback savedFeedback = siteFeedbackRepository.saveAndFlush(feedback);

                List<SiteFeedbackAttachment> savedAttachments = List.of();
                if (!preparedAttachments.isEmpty()) {
                    for (SiteFeedbackAttachment att : preparedAttachments) {
                        att.setFeedback(savedFeedback);
                    }
                    savedAttachments = siteFeedbackAttachmentRepository.saveAllAndFlush(preparedAttachments);
                }

                notificationWriter.notifyAdmins(
                        "Có phản hồi mới",
                        "Một phản hồi mới vừa được gửi tới Chợ PTITers.",
                        "ADMIN_FEEDBACK",
                        savedFeedback.getFeedbackId()
                );

                return new FeedbackCreateResponse(
                        savedFeedback.getFeedbackId(),
                        savedFeedback.getContent(),
                        savedFeedback.getCreatedAt(),
                        savedAttachments.stream().map(FeedbackAttachmentItem::from).toList()
                );
            });
            return response;
        } catch (ApiException e) {
            destroyAssetsBestEffort(uploadedAssets);
            throw e;
        } catch (RuntimeException e) {
            destroyAssetsBestEffort(uploadedAssets);
            log.error("DB error during feedback creation: {}", e.getMessage(), e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu phản hồi vào hệ thống.");
        }
    }

    private static byte[] readMultipartBytes(MultipartFile file, String filename) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Không thể đọc tệp '" + filename + "'.");
        }
    }

    private void destroyAssetsBestEffort(List<AssetRef> assets) {
        for (AssetRef ref : assets) {
            try {
                mediaStorage.destroyAsset(ref.storageKey(), ref.resourceType());
            } catch (Exception e) {
                log.warn("Failed to cleanup media asset {}: {}", ref.storageKey(), e.getMessage());
            }
        }
    }

    public PublicFeedbackCursorPage listPublicFeedbacks(int limit, String cursor) {
        int safeLimit = clampLimit(limit);
        FeedbackCursor decoded = decodeFeedbackCursor(cursor);
        List<SiteFeedback> fetched = feedbackQueryRepository.listPublicFeedbacks(
                safeLimit,
                decoded != null ? decoded.createdAt() : null,
                decoded != null ? decoded.feedbackId() : null
        );
        boolean hasMore = fetched.size() > safeLimit;
        List<SiteFeedback> pageItems = hasMore ? fetched.subList(0, safeLimit) : fetched;
        String nextCursor = (hasMore && !pageItems.isEmpty())
                ? encodeFeedbackCursor(pageItems.get(pageItems.size() - 1).getCreatedAt(),
                pageItems.get(pageItems.size() - 1).getFeedbackId())
                : null;

        List<PublicFeedbackItem> items = pageItems.stream()
                .map(f -> new PublicFeedbackItem(f.getFeedbackId(), f.getContent(), f.getCreatedAt()))
                .toList();
        return new PublicFeedbackCursorPage(items, nextCursor, hasMore);
    }

    public MyFeedbackCursorPage listMyFeedbacks(User currentUser, int limit, String cursor) {
        int safeLimit = clampLimit(limit);
        FeedbackCursor decoded = decodeFeedbackCursor(cursor);
        List<SiteFeedback> fetched = feedbackQueryRepository.listUserFeedbacks(
                currentUser.getUserId(),
                safeLimit,
                decoded != null ? decoded.createdAt() : null,
                decoded != null ? decoded.feedbackId() : null
        );
        boolean hasMore = fetched.size() > safeLimit;
        List<SiteFeedback> pageItems = hasMore ? fetched.subList(0, safeLimit) : fetched;
        String nextCursor = (hasMore && !pageItems.isEmpty())
                ? encodeFeedbackCursor(pageItems.get(pageItems.size() - 1).getCreatedAt(),
                pageItems.get(pageItems.size() - 1).getFeedbackId())
                : null;

        Map<Long, List<SiteFeedbackAttachment>> attachmentsByFeedback = feedbackQueryRepository
                .findAttachmentsByFeedbackIds(pageItems.stream().map(SiteFeedback::getFeedbackId).toList());

        List<MyFeedbackItem> items = pageItems.stream()
                .map(f -> new MyFeedbackItem(
                        f.getFeedbackId(),
                        f.getContent(),
                        f.getCreatedAt(),
                        attachmentsByFeedback.getOrDefault(f.getFeedbackId(), List.of())
                                .stream().map(FeedbackAttachmentItem::from).toList()
                ))
                .toList();
        return new MyFeedbackCursorPage(items, nextCursor, hasMore);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deleteMyFeedback(User currentUser, Long feedbackId) {
        if (currentUser.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("Tài khoản của bạn không có quyền thực hiện thao tác này.");
        }
        List<AssetRef> toDelete = transactionTemplate.execute(status -> {
            SiteFeedback feedback = feedbackQueryRepository.findFeedbackById(feedbackId, true)
                    .orElse(null);
            if (feedback == null || !feedback.getUserId().equals(currentUser.getUserId())) {
                throw ApiException.notFound("Không tìm thấy phản hồi.");
            }
            List<AssetRef> refs = feedback.getAttachments().stream()
                    .map(a -> new AssetRef(
                            a.getStorageKey(),
                            a.getKind() == FeedbackAttachmentKind.VIDEO ? "video" : "image"
                    ))
                    .toList();
            siteFeedbackRepository.delete(feedback);
            siteFeedbackRepository.flush();
            return refs;
        });
        if (toDelete != null && !toDelete.isEmpty()) {
            destroyAssetsBestEffort(toDelete);
        }
    }

    // =========================================================================
    // ADMIN FEEDBACK MANAGEMENT & EVIDENCE STREAMING
    // =========================================================================

    public AdminFeedbackListPage listAdminFeedbacks(int limit, String cursor) {
        int safeLimit = clampLimit(limit);
        FeedbackCursor decoded = decodeFeedbackCursor(cursor);
        List<SiteFeedback> fetched = feedbackQueryRepository.listAdminFeedbacks(
                safeLimit,
                decoded != null ? decoded.createdAt() : null,
                decoded != null ? decoded.feedbackId() : null
        );
        boolean hasMore = fetched.size() > safeLimit;
        List<SiteFeedback> pageItems = hasMore ? fetched.subList(0, safeLimit) : fetched;
        String nextCursor = (hasMore && !pageItems.isEmpty())
                ? encodeFeedbackCursor(pageItems.get(pageItems.size() - 1).getCreatedAt(),
                pageItems.get(pageItems.size() - 1).getFeedbackId())
                : null;

        Map<Long, List<SiteFeedbackAttachment>> attachmentsByFeedback = feedbackQueryRepository
                .findAttachmentsByFeedbackIds(pageItems.stream().map(SiteFeedback::getFeedbackId).toList());

        List<AdminFeedbackListItem> items = pageItems.stream().map(f -> {
            List<SiteFeedbackAttachment> atts = attachmentsByFeedback.getOrDefault(f.getFeedbackId(), List.of());
            int imgCount = (int) atts.stream().filter(a -> a.getKind() == FeedbackAttachmentKind.IMAGE).count();
            int vidCount = (int) atts.stream().filter(a -> a.getKind() == FeedbackAttachmentKind.VIDEO).count();
            return new AdminFeedbackListItem(
                    f.getFeedbackId(),
                    toAdminSender(f),
                    f.getContent(),
                    f.getCreatedAt(),
                    imgCount,
                    vidCount
            );
        }).toList();

        return new AdminFeedbackListPage(items, nextCursor, hasMore);
    }

    public AdminFeedbackDetail getAdminFeedbackDetail(Long feedbackId) {
        SiteFeedback feedback = feedbackQueryRepository.findFeedbackById(feedbackId, false)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phản hồi."));

        List<SiteFeedbackAttachment> attachments = feedbackQueryRepository
                .findAttachmentsByFeedbackIds(List.of(feedbackId))
                .getOrDefault(feedbackId, List.of());

        List<AdminFeedbackAttachmentItem> attDtos = attachments.stream()
                .map(a -> new AdminFeedbackAttachmentItem(
                        a.getAttachmentId(),
                        a.getKind().name(),
                        a.getMimeType(),
                        a.getSizeBytes(),
                        a.getDurationMs(),
                        "/api/v1/admin/feedback/" + feedback.getFeedbackId() + "/evidence/" + a.getAttachmentId()
                ))
                .toList();

        return new AdminFeedbackDetail(
                feedback.getFeedbackId(),
                toAdminSender(feedback),
                feedback.getContent(),
                feedback.getCreatedAt(),
                attDtos
        );
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deleteAdminFeedback(Long feedbackId) {
        List<AssetRef> toDelete = transactionTemplate.execute(status -> {
            SiteFeedback feedback = feedbackQueryRepository.findFeedbackById(feedbackId, true)
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy phản hồi."));
            List<AssetRef> refs = feedback.getAttachments().stream()
                    .map(a -> new AssetRef(
                            a.getStorageKey(),
                            a.getKind() == FeedbackAttachmentKind.VIDEO ? "video" : "image"
                    ))
                    .toList();
            siteFeedbackRepository.delete(feedback);
            siteFeedbackRepository.flush();
            return refs;
        });
        if (toDelete != null && !toDelete.isEmpty()) {
            destroyAssetsBestEffort(toDelete);
        }
    }

    public EvidenceStreamResult streamFeedbackEvidence(Long feedbackId, Long attachmentId, String rangeHeader) {
        SiteFeedbackAttachment attachment = feedbackQueryRepository.findAttachment(feedbackId, attachmentId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tệp minh chứng."));

        String targetUrl = attachment.getMediaUrl();
        if (targetUrl == null || targetUrl.isBlank()) {
            throw ApiException.notFound("Tệp minh chứng không có liên kết hợp lệ.");
        }

        if (mediaStorage instanceof LocalMediaStorage localMediaStorage
                && targetUrl.startsWith(LocalMediaStorage.BASE_URL)) {
            return streamLocalEvidence(localMediaStorage, attachment, rangeHeader);
        }

        validateSafeRemoteMediaUrl(targetUrl);
        return streamRemoteEvidence(attachment, targetUrl, rangeHeader);
    }

    private EvidenceStreamResult streamLocalEvidence(
            LocalMediaStorage localMediaStorage,
            SiteFeedbackAttachment attachment,
            String rangeHeader
    ) {
        Path baseDir = localMediaStorage.getBaseDir().toAbsolutePath().normalize();
        Path file = baseDir.resolve(attachment.getStorageKey()).normalize();
        if (!file.startsWith(baseDir) || !Files.exists(file)) {
            throw ApiException.notFound("Không tìm thấy tệp minh chứng.");
        }
        try {
            long totalSize = Files.size(file);
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE,
                    attachment.getMimeType() != null ? attachment.getMimeType() : "application/octet-stream");
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
            headers.set(HttpHeaders.CACHE_CONTROL, "private, max-age=3600");

            long start = 0;
            long end = totalSize - 1;
            int statusCode = HttpStatus.OK.value();

            if (rangeHeader != null && rangeHeader.trim().toLowerCase(Locale.ROOT).startsWith("bytes=")) {
                String spec = rangeHeader.trim().substring("bytes=".length()).split(",")[0].trim();
                String[] parts = spec.split("-", 2);
                if (!parts[0].isEmpty()) {
                    start = Long.parseLong(parts[0]);
                    if (parts.length > 1 && !parts[1].isEmpty()) {
                        end = Math.min(Long.parseLong(parts[1]), totalSize - 1);
                    }
                } else if (parts.length > 1 && !parts[1].isEmpty()) {
                    long suffix = Long.parseLong(parts[1]);
                    start = Math.max(0, totalSize - suffix);
                }
                if (start < 0 || start >= totalSize || end < start) {
                    throw new ApiException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "Dải byte không hợp lệ.");
                }
                statusCode = HttpStatus.PARTIAL_CONTENT.value();
                headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + totalSize);
            }

            long contentLength = end - start + 1;
            headers.setContentLength(contentLength);

            final long rangeStart = start;
            final long bytesToSend = contentLength;
            SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ);
            channel.position(rangeStart);
            InputStream boundedStream = new InputStream() {
                private long remaining = bytesToSend;

                @Override
                public int read() throws IOException {
                    byte[] one = new byte[1];
                    int n = read(one, 0, 1);
                    return n == -1 ? -1 : (one[0] & 0xFF);
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    if (remaining <= 0) {
                        return -1;
                    }
                    int toRead = (int) Math.min(len, remaining);
                    ByteBuffer bb = ByteBuffer.wrap(b, off, toRead);
                    int n = channel.read(bb);
                    if (n > 0) {
                        remaining -= n;
                    }
                    return n;
                }

                @Override
                public void close() throws IOException {
                    channel.close();
                }
            };
            return new EvidenceStreamResult(statusCode, headers, new InputStreamResource(boundedStream));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to read local feedback evidence {}: {}", attachment.getAttachmentId(), e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể đọc tệp minh chứng.");
        }
    }

    private EvidenceStreamResult streamRemoteEvidence(
            SiteFeedbackAttachment attachment,
            String targetUrl,
            String rangeHeader
    ) {
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(30))
                    .GET();
            if (rangeHeader != null && !rangeHeader.isBlank()) {
                reqBuilder.header("Range", rangeHeader.trim());
            }

            HttpResponse<InputStream> upstream = httpClient.send(
                    reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofInputStream()
            );
            if (upstream.statusCode() >= 400) {
                upstream.body().close();
                log.error("Upstream returned {} for feedback attachment {}",
                        upstream.statusCode(), attachment.getAttachmentId());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Không thể tải tệp từ máy chủ lưu trữ.");
            }

            HttpHeaders headers = new HttpHeaders();
            String contentType = attachment.getMimeType() != null
                    ? attachment.getMimeType()
                    : upstream.headers().firstValue("content-type").orElse("application/octet-stream");
            headers.set(HttpHeaders.CONTENT_TYPE, contentType);
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
            headers.set(HttpHeaders.CACHE_CONTROL, "private, max-age=3600");

            if (upstream.statusCode() == 206) {
                upstream.headers().firstValue("content-range")
                        .ifPresent(cr -> headers.set(HttpHeaders.CONTENT_RANGE, cr));
            }
            upstream.headers().firstValue("content-length").ifPresent(cl -> {
                try {
                    long len = Long.parseLong(cl);
                    if (len > 0) {
                        headers.setContentLength(len);
                    }
                } catch (NumberFormatException ignored) {
                }
            });

            return new EvidenceStreamResult(upstream.statusCode(), headers, new InputStreamResource(upstream.body()));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to connect upstream storage for attachment {}: {}",
                    attachment.getAttachmentId(), e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Không thể kết nối đến máy chủ lưu trữ tệp.");
        }
    }

    private static void validateSafeRemoteMediaUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            throw ApiException.badRequest("Giao thức liên kết tệp không an toàn.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw ApiException.badRequest("Giao thức liên kết tệp không an toàn.");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"res.cloudinary.com".equals(host) && !host.endsWith(".res.cloudinary.com")) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Nguồn lưu trữ tệp không được tin cậy.");
        }
    }

    private static AdminFeedbackSender toAdminSender(SiteFeedback f) {
        User u = f.getUser();
        return new AdminFeedbackSender(
                f.getUserId(),
                u != null ? u.getFullName() : "Người dùng #" + f.getUserId(),
                u != null ? u.getAvatarUrl() : null
        );
    }

    // =========================================================================
    // SUPPORT INFO & LEADERBOARD
    // =========================================================================

    public SupportResponse getSupportInfo() {
        String providerUid = trimToNull(firstProperty(
                "COMMUNITY_CREATOR_PROVIDER_UID", "community.creator-provider-uid"));
        String creatorEmail = trimToNull(firstProperty(
                "COMMUNITY_CREATOR_EMAIL", "community.creator-email"));

        User creatorUser = null;
        if (providerUid != null) {
            creatorUser = feedbackQueryRepository.findUserByFirebaseProviderUid(providerUid).orElse(null);
            if (creatorUser == null) {
                log.warn("Configured COMMUNITY_CREATOR_PROVIDER_UID could not be resolved to any local user");
            }
        } else if (creatorEmail != null) {
            creatorUser = feedbackQueryRepository.findUserByEmailIgnoreCase(creatorEmail).orElse(null);
            if (creatorUser == null) {
                log.warn("Configured COMMUNITY_CREATOR_EMAIL could not be resolved to any local user");
            }
        }

        CreatorPublicInfo creatorInfo = null;
        String profilePath = null;
        if (creatorUser != null && creatorUser.getStatus() == UserStatus.ACTIVE) {
            creatorInfo = new CreatorPublicInfo(
                    creatorUser.getUserId(),
                    creatorUser.getFullName(),
                    creatorUser.getAvatarUrl(),
                    CREATOR_TITLE
            );
            profilePath = "/users/" + creatorUser.getUserId();
        }

        return new SupportResponse(creatorInfo, profilePath, DEFAULT_QR_URL, DEFAULT_SUPPORT_MESSAGE);
    }

    private String firstProperty(String key1, String key2) {
        String v1 = environment.getProperty(key1);
        if (v1 != null && !v1.isBlank()) {
            return v1;
        }
        return environment.getProperty(key2);
    }

    public SupporterLeaderboardResponse getSupporterLeaderboard() {
        return new SupporterLeaderboardResponse(feedbackQueryRepository.getSupporterLeaderboard(10));
    }

    // =========================================================================
    // ADMIN SUPPORT CONTRIBUTIONS
    // =========================================================================

    public List<SupporterResolveResponse> searchSupporters(String query, int limit) {
        if (query == null || query.isBlank() || query.length() > 100) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Từ khóa tìm kiếm phải từ 1 đến 100 ký tự.");
        }
        return feedbackQueryRepository.searchSupportersForAdmin(query, limit).stream()
                .map(u -> new SupporterResolveResponse(
                        u.getUserId(),
                        u.getFullName(),
                        u.getAvatarUrl(),
                        "/users/" + u.getUserId()
                ))
                .toList();
    }

    @Transactional
    public AdminSupportContributionItem createSupportContribution(
            User currentAdmin,
            AdminSupportContributionCreate request
    ) {
        userRepository.findById(request.supporterId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng ủng hộ."));

        SupportContribution saved = supportContributionRepository.saveAndFlush(
                SupportContribution.builder()
                        .supporterId(request.supporterId())
                        .amountVnd(request.amountVnd())
                        .receivedAt(request.receivedAt())
                        .isPublic(Boolean.TRUE.equals(request.isPublic()))
                        .recordedBy(currentAdmin.getUserId())
                        .transactionRef(trimToNull(request.transactionRef()))
                        .adminNote(trimToNull(request.adminNote()))
                        .build()
        );

        SupportContribution loaded = feedbackQueryRepository.findContributionById(saved.getContributionId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bản ghi đóng góp."));
        return toAdminContributionItem(loaded);
    }

    public AdminSupportContributionCursorPage listSupportContributions(int limit, String cursor) {
        int safeLimit = clampLimit(limit);
        SupportCursor decoded = decodeSupportCursor(cursor);
        List<SupportContribution> fetched = feedbackQueryRepository.listAdminContributions(
                safeLimit,
                decoded != null ? decoded.timestamp() : null,
                decoded != null ? decoded.id() : null
        );
        boolean hasMore = fetched.size() > safeLimit;
        List<SupportContribution> pageItems = hasMore ? fetched.subList(0, safeLimit) : fetched;
        String nextCursor = (hasMore && !pageItems.isEmpty())
                ? encodeSupportCursor(pageItems.get(pageItems.size() - 1).getReceivedAt(),
                pageItems.get(pageItems.size() - 1).getContributionId())
                : null;

        List<AdminSupportContributionItem> items = pageItems.stream()
                .map(FeedbackService::toAdminContributionItem)
                .toList();
        return new AdminSupportContributionCursorPage(items, nextCursor, hasMore);
    }

    @Transactional
    public AdminSupportContributionItem updateSupportContributionVisibility(
            Long contributionId,
            AdminSupportContributionVisibilityUpdate request
    ) {
        SupportContribution contribution = feedbackQueryRepository.findContributionById(contributionId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bản ghi đóng góp."));
        contribution.setIsPublic(request.isPublic());
        supportContributionRepository.saveAndFlush(contribution);
        return toAdminContributionItem(contribution);
    }

    @Transactional
    public void deleteSupportContribution(Long contributionId) {
        SupportContribution contribution = supportContributionRepository.findById(contributionId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bản ghi đóng góp."));
        supportContributionRepository.delete(contribution);
        supportContributionRepository.flush();
    }

    private static AdminSupportContributionItem toAdminContributionItem(SupportContribution c) {
        return new AdminSupportContributionItem(
                c.getContributionId(),
                toSafeIdentity(c.getSupporter(), c.getSupporterId()),
                c.getAmountVnd(),
                c.getIsPublic(),
                c.getReceivedAt(),
                c.getTransactionRef(),
                c.getAdminNote(),
                toSafeIdentity(c.getRecorder(), c.getRecordedBy()),
                c.getCreatedAt()
        );
    }

    private static AdminUserSafeIdentity toSafeIdentity(User u, Long fallbackId) {
        return new AdminUserSafeIdentity(
                u != null ? u.getUserId() : fallbackId,
                u != null ? u.getFullName() : "Người dùng #" + fallbackId,
                u != null ? u.getAvatarUrl() : null
        );
    }

    // =========================================================================
    // SUPPORT CLAIMS (USER & ADMIN RECONCILIATION)
    // =========================================================================

    @Transactional
    public SupportClaimUserItem submitSupportClaim(User currentUser, SupportClaimCreate request) {
        String senderAccountName = trimToNull(request.senderAccountName());
        if (senderAccountName == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Tên chủ tài khoản chuyển tiền không được để trống");
        }

        SupportClaim saved = supportClaimRepository.saveAndFlush(
                SupportClaim.builder()
                        .supporterId(currentUser.getUserId())
                        .claimedAmountVnd(request.claimedAmountVnd())
                        .claimedTransferredAt(request.claimedTransferredAt())
                        .senderAccountName(senderAccountName)
                        .wantsPublic(Boolean.TRUE.equals(request.wantsPublic()))
                        .status(SupportClaimStatus.PENDING)
                        .build()
        );

        notificationWriter.notifyAdmins(
                "Có khoản ủng hộ chờ đối soát",
                "Một yêu cầu xác nhận ủng hộ mới đang chờ đối soát.",
                "ADMIN_SUPPORT_CLAIM",
                saved.getClaimId()
        );

        return SupportClaimUserItem.from(saved);
    }

    public SupportClaimUserListPage listMySupportClaims(User currentUser, int limit) {
        List<SupportClaimUserItem> items = feedbackQueryRepository
                .listUserSupportClaims(currentUser.getUserId(), limit)
                .stream()
                .map(SupportClaimUserItem::from)
                .toList();
        return new SupportClaimUserListPage(items);
    }

    public AdminSupportClaimCursorPage listSupportClaims(String statusFilterRaw, int limit, String cursor) {
        SupportClaimStatus statusFilter = null;
        if (statusFilterRaw != null && !statusFilterRaw.isBlank()) {
            try {
                statusFilter = SupportClaimStatus.valueOf(statusFilterRaw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Trạng thái lọc không hợp lệ: " + statusFilterRaw);
            }
        }

        int safeLimit = clampLimit(limit);
        SupportCursor decoded = decodeSupportCursor(cursor);
        List<SupportClaim> fetched = feedbackQueryRepository.listAdminSupportClaims(
                statusFilter,
                safeLimit,
                decoded != null ? decoded.timestamp() : null,
                decoded != null ? decoded.id() : null
        );
        boolean hasMore = fetched.size() > safeLimit;
        List<SupportClaim> pageItems = hasMore ? fetched.subList(0, safeLimit) : fetched;
        String nextCursor = (hasMore && !pageItems.isEmpty())
                ? encodeSupportCursor(pageItems.get(pageItems.size() - 1).getCreatedAt(),
                pageItems.get(pageItems.size() - 1).getClaimId())
                : null;

        List<AdminSupportClaimItem> items = pageItems.stream()
                .map(FeedbackService::toAdminClaimItem)
                .toList();
        return new AdminSupportClaimCursorPage(items, nextCursor, hasMore);
    }

    @Transactional
    public AdminSupportClaimItem verifySupportClaim(
            Long claimId,
            User currentAdmin,
            AdminSupportClaimVerify request
    ) {
        SupportClaim claim = feedbackQueryRepository.findSupportClaimById(claimId, true)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy yêu cầu xác nhận ủng hộ."));

        if (claim.getStatus() == SupportClaimStatus.VERIFIED) {
            throw ApiException.conflict("Yêu cầu xác nhận này đã được xác thực trước đó.");
        }
        if (claim.getStatus() == SupportClaimStatus.REJECTED) {
            throw ApiException.conflict("Yêu cầu xác nhận này đã bị từ chối trước đó.");
        }
        if (claim.getStatus() != SupportClaimStatus.PENDING) {
            throw ApiException.badRequest("Trạng thái yêu cầu không hợp lệ để xác thực: " + claim.getStatus());
        }

        userRepository.findById(claim.getSupporterId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng ủng hộ."));

        SupportContribution contribution = supportContributionRepository.saveAndFlush(
                SupportContribution.builder()
                        .supporterId(claim.getSupporterId())
                        .amountVnd(request.amountVnd())
                        .receivedAt(request.receivedAt())
                        .isPublic(Boolean.TRUE.equals(claim.getWantsPublic()))
                        .recordedBy(currentAdmin.getUserId())
                        .transactionRef(trimToNull(request.transactionRef()))
                        .adminNote(trimToNull(request.adminNote()))
                        .build()
        );

        claim.setStatus(SupportClaimStatus.VERIFIED);
        claim.setReviewedBy(currentAdmin.getUserId());
        claim.setReviewedAt(OffsetDateTime.now(ZoneOffset.UTC));
        claim.setContributionId(contribution.getContributionId());
        supportClaimRepository.saveAndFlush(claim);

        String body = String.format(
                Locale.US,
                "Khoản ủng hộ %,dđ của bạn đã được xác nhận. Cảm ơn bạn đã đồng hành cùng Chợ PTITers.",
                request.amountVnd()
        );
        notificationWriter.create(
                claim.getSupporterId(),
                NotificationType.SYSTEM,
                "Khoản ủng hộ đã được xác nhận",
                body,
                "SUPPORT_CLAIM",
                claim.getClaimId()
        );

        SupportClaim reloaded = feedbackQueryRepository.findSupportClaimById(claimId, false)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy yêu cầu xác nhận ủng hộ."));
        return toAdminClaimItem(reloaded);
    }

    @Transactional
    public AdminSupportClaimItem rejectSupportClaim(
            Long claimId,
            User currentAdmin,
            AdminSupportClaimReject request
    ) {
        SupportClaim claim = feedbackQueryRepository.findSupportClaimById(claimId, true)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy yêu cầu xác nhận ủng hộ."));

        if (claim.getStatus() == SupportClaimStatus.VERIFIED) {
            throw ApiException.conflict("Yêu cầu xác nhận này đã được xác thực trước đó, không thể từ chối.");
        }
        if (claim.getStatus() == SupportClaimStatus.REJECTED) {
            throw ApiException.conflict("Yêu cầu xác nhận này đã bị từ chối trước đó.");
        }
        if (claim.getStatus() != SupportClaimStatus.PENDING) {
            throw ApiException.badRequest("Trạng thái yêu cầu không hợp lệ để từ chối: " + claim.getStatus());
        }

        String reason = request != null ? trimToNull(request.rejectionReason()) : null;
        claim.setStatus(SupportClaimStatus.REJECTED);
        claim.setReviewedBy(currentAdmin.getUserId());
        claim.setReviewedAt(OffsetDateTime.now(ZoneOffset.UTC));
        claim.setRejectionReason(reason);
        supportClaimRepository.saveAndFlush(claim);

        String body = reason != null
                ? "Yêu cầu xác nhận chưa được chấp thuận: " + reason
                : "Khoản ủng hộ bạn khai báo chưa khớp với giao dịch nhận tiền thực tế. Vui lòng kiểm tra lại.";
        notificationWriter.create(
                claim.getSupporterId(),
                NotificationType.SYSTEM,
                "Chưa xác nhận được khoản ủng hộ",
                body,
                "SUPPORT_CLAIM",
                claim.getClaimId()
        );

        SupportClaim reloaded = feedbackQueryRepository.findSupportClaimById(claimId, false)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy yêu cầu xác nhận ủng hộ."));
        return toAdminClaimItem(reloaded);
    }

    private static AdminSupportClaimItem toAdminClaimItem(SupportClaim c) {
        User sup = c.getSupporter();
        SupporterResolveResponse supporterDto = new SupporterResolveResponse(
                c.getSupporterId(),
                sup != null ? sup.getFullName() : "Người dùng #" + c.getSupporterId(),
                sup != null ? sup.getAvatarUrl() : null,
                "/users/" + c.getSupporterId()
        );

        AdminUserSafeIdentity reviewerDto = null;
        if (c.getReviewer() != null) {
            reviewerDto = new AdminUserSafeIdentity(
                    c.getReviewer().getUserId(),
                    c.getReviewer().getFullName(),
                    c.getReviewer().getAvatarUrl()
            );
        } else if (c.getReviewedBy() != null) {
            reviewerDto = new AdminUserSafeIdentity(c.getReviewedBy(), "Quản trị viên", null);
        }

        return new AdminSupportClaimItem(
                c.getClaimId(),
                supporterDto,
                c.getClaimedAmountVnd(),
                c.getClaimedTransferredAt(),
                c.getSenderAccountName(),
                c.getWantsPublic(),
                c.getStatus().name(),
                reviewerDto,
                c.getReviewedAt(),
                c.getContributionId(),
                c.getRejectionReason(),
                c.getCreatedAt()
        );
    }
}
