package com.ptiters.marketplace.tutorial;

import com.ptiters.marketplace.exception.ApiException;
import com.ptiters.marketplace.integration.MediaStorage;
import com.ptiters.marketplace.integration.MediaStorageException;
import com.ptiters.marketplace.integration.MediaStorageNotConfiguredException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TutorialService {

    public static final long MAX_TUTORIAL_VIDEO_SIZE_BYTES = 100L * 1024 * 1024;

    public static final Set<String> ALLOWED_FEATURE_KEYS = Set.of(
            "HOME",
            "FAVORITES",
            "CREATE_PRODUCT",
            "MY_LISTINGS",
            "BUYER_ORDERS",
            "SELLER_ORDERS",
            "NOTIFICATIONS",
            "MESSAGES",
            "PROFILE",
            "FEEDBACK"
    );

    public static final Set<String> ALLOWED_ACTION_KEYS = Set.of(
            "HOME",
            "CREATE_PRODUCT",
            "MY_LISTINGS",
            "FAVORITES",
            "BUYER_ORDERS",
            "SELLER_ORDERS",
            "MESSAGES",
            "NOTIFICATIONS",
            "FEEDBACK",
            "PROFILE"
    );

    private static final Set<String> ALLOWED_VIDEO_MIMES = Set.of("video/mp4");
    private static final Set<String> ALLOWED_VIDEO_FORMATS = Set.of("mp4", "m4v");

    private final TutorialRepository tutorialRepository;
    private final TutorialQueryRepository queryRepository;
    private final MediaStorage mediaStorage;
    private final TransactionTemplate transactionTemplate;

    private record ValidatedVideoResource(
            String deliveryUrl,
            String storageKey,
            long videoSizeBytes,
            Long durationMs
    ) {
    }

    @Transactional(readOnly = true)
    public List<PublicTutorialResponse> listPublicTutorials(String featureKey) {
        return queryRepository.findPublishedTutorials(featureKey).stream()
                .map(this::toPublicResponse)
                .toList();
    }

    public TutorialUploadSignatureResponse getUploadSignature() {
        String sessionId = "tut_up_" + UUID.randomUUID().toString().replace("-", "");
        MediaStorage.TutorialUploadSignature sig;
        try {
            sig = mediaStorage.generateTutorialUploadSignature(sessionId);
        } catch (MediaStorageNotConfiguredException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ lưu trữ video Cloudinary chưa được cấu hình.");
        }
        return new TutorialUploadSignatureResponse(
                sig.cloudName(),
                sig.apiKey(),
                sig.timestamp(),
                sig.signature(),
                sig.folder(),
                sig.publicId(),
                sig.overwrite(),
                sig.uploadSessionId()
        );
    }

    @Transactional(readOnly = true)
    public List<AdminTutorialResponse> listAdminTutorials(String featureKey) {
        return queryRepository.findAllTutorials(featureKey).stream()
                .map(this::toAdminResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminTutorialResponse getAdminTutorialDetail(Long tutorialId) {
        Tutorial tutorial = tutorialRepository.findById(tutorialId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy Tutorial với ID " + tutorialId));
        return toAdminResponse(tutorial);
    }

    public AdminTutorialResponse createDirectTutorial(Long adminId, AdminTutorialCreateDirectRequest request) {
        String featureKey = validateFeatureKey(request.featureKey(), HttpStatus.UNPROCESSABLE_ENTITY);
        String title = validateTitle(request.title(), HttpStatus.UNPROCESSABLE_ENTITY);
        String description = validateDescription(request.description(), HttpStatus.UNPROCESSABLE_ENTITY);
        String actionKey = validateActionKey(request.actionKey(), HttpStatus.UNPROCESSABLE_ENTITY);
        boolean isPublished = Boolean.TRUE.equals(request.isPublished());

        String cleanPublicId = validateDirectPublicIdForSession(request.publicId(), request.uploadSessionId());

        boolean duplicateKey = Boolean.TRUE.equals(transactionTemplate.execute(
                s -> queryRepository.findByStorageKey(cleanPublicId).isPresent()
        ));
        if (duplicateKey) {
            throw ApiException.badRequest("Tệp video này đã thuộc về một video hướng dẫn khác.");
        }

        ValidatedVideoResource verified = verifyDirectVideoResource(
                cleanPublicId,
                request.secureUrl(),
                request.videoSizeBytes(),
                request.durationMs()
        );

        try {
            Tutorial created = transactionTemplate.execute(status -> {
                int maxOrder = queryRepository.getMaxSortOrder(featureKey);
                Tutorial tutorial = Tutorial.builder()
                        .featureKey(featureKey)
                        .title(title)
                        .description(description)
                        .videoUrl(verified.deliveryUrl())
                        .videoStorageKey(verified.storageKey())
                        .videoMimeType("video/mp4")
                        .videoSizeBytes(verified.videoSizeBytes())
                        .durationMs(verified.durationMs())
                        .actionKey(actionKey)
                        .sortOrder(maxOrder + 10)
                        .isPublished(isPublished)
                        .createdBy(adminId)
                        .build();
                return tutorialRepository.saveAndFlush(tutorial);
            });
            if (created == null) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể tạo Tutorial.");
            }
            return toAdminResponse(created);
        } catch (RuntimeException e) {
            safeDestroyVideo(verified.storageKey());
            throw e;
        }
    }

    public AdminTutorialResponse createTutorial(
            Long adminId,
            String rawFeatureKey,
            String rawTitle,
            String rawDescription,
            String rawActionKey,
            boolean isPublished,
            MultipartFile videoFile
    ) {
        String featureKey = validateFeatureKey(rawFeatureKey, HttpStatus.BAD_REQUEST);
        String title = validateTitle(rawTitle, HttpStatus.BAD_REQUEST);
        String description = validateDescription(rawDescription, HttpStatus.BAD_REQUEST);
        String actionKey = validateActionKey(rawActionKey, HttpStatus.BAD_REQUEST);

        byte[] videoBytes = readAndValidateMultipartVideo(videoFile);

        String filename = (videoFile.getOriginalFilename() == null || videoFile.getOriginalFilename().isBlank())
                ? "tutorial.mp4"
                : videoFile.getOriginalFilename();
        MediaStorage.UploadedAsset uploaded = uploadMultipartVideo(videoBytes, filename);
        Long durationMs = resolveDurationMs(uploaded.durationSeconds(), null);
        if (durationMs != null && durationMs <= 0) {
            safeDestroyVideo(uploaded.publicId());
            throw ApiException.badRequest("Thời lượng video phải lớn hơn 0.");
        }
        String deliveryUrl = resolveVideoDeliveryUrl(uploaded.secureUrl(), uploaded.publicId());

        try {
            Tutorial created = transactionTemplate.execute(status -> {
                int maxOrder = queryRepository.getMaxSortOrder(featureKey);
                Tutorial tutorial = Tutorial.builder()
                        .featureKey(featureKey)
                        .title(title)
                        .description(description)
                        .videoUrl(deliveryUrl)
                        .videoStorageKey(uploaded.publicId())
                        .videoMimeType("video/mp4")
                        .videoSizeBytes((long) videoBytes.length)
                        .durationMs(durationMs)
                        .actionKey(actionKey)
                        .sortOrder(maxOrder + 10)
                        .isPublished(isPublished)
                        .createdBy(adminId)
                        .build();
                return tutorialRepository.saveAndFlush(tutorial);
            });
            if (created == null) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể tạo Tutorial.");
            }
            return toAdminResponse(created);
        } catch (RuntimeException e) {
            safeDestroyVideo(uploaded.publicId());
            throw e;
        }
    }

    @Transactional
    public List<AdminTutorialResponse> reorderTutorials(AdminTutorialReorderRequest request) {
        String featureKey = validateFeatureKey(request.featureKey(), HttpStatus.UNPROCESSABLE_ENTITY);
        List<Long> ids = request.tutorialIds();
        if (ids == null || ids.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Danh sách tutorial_ids không được để trống.");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Danh sách tutorial_ids không được chứa ID trùng lặp.");
        }

        List<Tutorial> tutorials = queryRepository.findByIdsForUpdate(ids);
        if (tutorials.size() != ids.size()) {
            throw ApiException.badRequest("Một hoặc nhiều ID Tutorial không tồn tại trong hệ thống.");
        }

        Map<Long, Tutorial> byId = new HashMap<>();
        for (Tutorial t : tutorials) {
            byId.put(t.getTutorialId(), t);
        }

        for (Long tid : ids) {
            Tutorial t = byId.get(tid);
            if (!featureKey.equals(t.getFeatureKey())) {
                throw ApiException.badRequest("Tutorial ID " + tid + " không thuộc tính năng '" + featureKey + "'.");
            }
        }

        for (int i = 0; i < ids.size(); i++) {
            byId.get(ids.get(i)).setSortOrder((i + 1) * 10);
        }
        tutorialRepository.flush();

        return queryRepository.findAllTutorials(featureKey).stream()
                .map(this::toAdminResponse)
                .toList();
    }

    @Transactional
    public AdminTutorialResponse updateTutorialMetadata(Long tutorialId, AdminTutorialUpdateRequest request) {
        String newFeatureKey = request.getFeatureKey() != null
                ? validateFeatureKey(request.getFeatureKey(), HttpStatus.UNPROCESSABLE_ENTITY)
                : null;
        String newTitle = request.getTitle() != null
                ? validateTitle(request.getTitle(), HttpStatus.UNPROCESSABLE_ENTITY)
                : null;
        String newDescription = request.isDescriptionSet()
                ? validateDescription(request.getDescription(), HttpStatus.UNPROCESSABLE_ENTITY)
                : null;
        String newActionKey = request.isActionKeySet()
                ? validateActionKey(request.getActionKey(), HttpStatus.UNPROCESSABLE_ENTITY)
                : null;

        Tutorial tutorial = queryRepository.findByIdForUpdate(tutorialId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy Tutorial với ID " + tutorialId));

        if (newFeatureKey != null && !newFeatureKey.equals(tutorial.getFeatureKey())) {
            int maxOrder = queryRepository.getMaxSortOrder(newFeatureKey);
            tutorial.setFeatureKey(newFeatureKey);
            tutorial.setSortOrder(maxOrder + 10);
        }
        if (newTitle != null) {
            tutorial.setTitle(newTitle);
        }
        if (request.isDescriptionSet()) {
            tutorial.setDescription(newDescription);
        }
        if (request.isActionKeySet()) {
            tutorial.setActionKey(newActionKey);
        }
        if (request.getIsPublished() != null) {
            tutorial.setIsPublished(request.getIsPublished());
        }

        Tutorial saved = tutorialRepository.saveAndFlush(tutorial);
        return toAdminResponse(saved);
    }

    public AdminTutorialResponse replaceTutorialVideo(Long tutorialId, MultipartFile videoFile) {
        boolean exists = Boolean.TRUE.equals(transactionTemplate.execute(
                s -> tutorialRepository.existsById(tutorialId)
        ));
        if (!exists) {
            throw ApiException.notFound("Không tìm thấy Tutorial với ID " + tutorialId);
        }

        byte[] videoBytes = readAndValidateMultipartVideo(videoFile);
        String filename = (videoFile.getOriginalFilename() == null || videoFile.getOriginalFilename().isBlank())
                ? "tutorial.mp4"
                : videoFile.getOriginalFilename();
        MediaStorage.UploadedAsset uploaded = uploadMultipartVideo(videoBytes, filename);
        Long durationMs = resolveDurationMs(uploaded.durationSeconds(), null);
        if (durationMs != null && durationMs <= 0) {
            safeDestroyVideo(uploaded.publicId());
            throw ApiException.badRequest("Thời lượng video phải lớn hơn 0.");
        }
        String deliveryUrl = resolveVideoDeliveryUrl(uploaded.secureUrl(), uploaded.publicId());

        record ReplaceOutcome(AdminTutorialResponse response, String oldStorageKey) {
        }

        ReplaceOutcome outcome;
        try {
            outcome = transactionTemplate.execute(status -> {
                Tutorial tutorial = queryRepository.findByIdForUpdate(tutorialId)
                        .orElseThrow(() -> ApiException.notFound("Không tìm thấy Tutorial với ID " + tutorialId));
                String oldKey = tutorial.getVideoStorageKey();
                tutorial.setVideoUrl(deliveryUrl);
                tutorial.setVideoStorageKey(uploaded.publicId());
                tutorial.setVideoMimeType("video/mp4");
                tutorial.setVideoSizeBytes((long) videoBytes.length);
                tutorial.setDurationMs(durationMs);
                Tutorial saved = tutorialRepository.saveAndFlush(tutorial);
                return new ReplaceOutcome(toAdminResponse(saved), oldKey);
            });
        } catch (RuntimeException e) {
            safeDestroyVideo(uploaded.publicId());
            throw e;
        }

        if (outcome != null && outcome.oldStorageKey() != null
                && !outcome.oldStorageKey().equals(uploaded.publicId())) {
            safeDestroyVideo(outcome.oldStorageKey());
        }

        return outcome != null ? outcome.response() : null;
    }

    public AdminTutorialResponse confirmDirectTutorialVideo(
            Long tutorialId,
            AdminTutorialConfirmDirectVideo request
    ) {
        Tutorial existing = transactionTemplate.execute(
                s -> tutorialRepository.findById(tutorialId).orElse(null)
        );
        if (existing == null) {
            throw ApiException.notFound("Không tìm thấy Tutorial với ID " + tutorialId);
        }

        String cleanPublicId = validateDirectPublicIdForSession(request.publicId(), request.uploadSessionId());

        // Idempotent replay check
        if (cleanPublicId.equals(existing.getVideoStorageKey())) {
            return toAdminResponse(existing);
        }

        Tutorial ownerOfKey = transactionTemplate.execute(
                s -> queryRepository.findByStorageKey(cleanPublicId).orElse(null)
        );
        if (ownerOfKey != null && !ownerOfKey.getTutorialId().equals(tutorialId)) {
            throw ApiException.badRequest("Tệp video này đã thuộc về một video hướng dẫn khác.");
        }

        ValidatedVideoResource verified = verifyDirectVideoResource(
                cleanPublicId,
                request.secureUrl(),
                request.videoSizeBytes(),
                request.durationMs()
        );

        record ConfirmOutcome(AdminTutorialResponse response, String oldStorageKey, boolean replaced) {
        }

        ConfirmOutcome outcome;
        try {
            outcome = transactionTemplate.execute(status -> {
                Tutorial tutorial = queryRepository.findByIdForUpdate(tutorialId)
                        .orElseThrow(() -> ApiException.notFound("Không tìm thấy Tutorial với ID " + tutorialId));
                if (cleanPublicId.equals(tutorial.getVideoStorageKey())) {
                    return new ConfirmOutcome(toAdminResponse(tutorial), null, false);
                }
                String oldKey = tutorial.getVideoStorageKey();
                tutorial.setVideoUrl(verified.deliveryUrl());
                tutorial.setVideoStorageKey(verified.storageKey());
                tutorial.setVideoMimeType("video/mp4");
                tutorial.setVideoSizeBytes(verified.videoSizeBytes());
                tutorial.setDurationMs(verified.durationMs());
                Tutorial saved = tutorialRepository.saveAndFlush(tutorial);
                return new ConfirmOutcome(toAdminResponse(saved), oldKey, true);
            });
        } catch (RuntimeException e) {
            safeDestroyVideo(verified.storageKey());
            throw e;
        }

        if (outcome != null && outcome.replaced() && outcome.oldStorageKey() != null
                && !outcome.oldStorageKey().equals(verified.storageKey())) {
            safeDestroyVideo(outcome.oldStorageKey());
        }

        return outcome != null ? outcome.response() : null;
    }

    public Map<String, String> deleteTutorial(Long tutorialId) {
        String storageKey = transactionTemplate.execute(status -> {
            Tutorial tutorial = queryRepository.findByIdForUpdate(tutorialId)
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy Tutorial với ID " + tutorialId));
            String key = tutorial.getVideoStorageKey();
            tutorialRepository.delete(tutorial);
            tutorialRepository.flush();
            return key;
        });

        safeDestroyVideo(storageKey);
        return Map.of("message", "Đã xóa Tutorial thành công");
    }

    private PublicTutorialResponse toPublicResponse(Tutorial t) {
        return new PublicTutorialResponse(
                t.getTutorialId(),
                t.getFeatureKey(),
                t.getTitle(),
                t.getDescription(),
                mediaStorage.tutorialVideoDeliveryUrl(t.getVideoUrl()),
                deriveThumbnailUrl(t),
                t.getDurationMs(),
                t.getActionKey(),
                t.getSortOrder()
        );
    }

    private AdminTutorialResponse toAdminResponse(Tutorial t) {
        return new AdminTutorialResponse(
                t.getTutorialId(),
                t.getFeatureKey(),
                t.getTitle(),
                t.getDescription(),
                mediaStorage.tutorialVideoDeliveryUrl(t.getVideoUrl()),
                t.getVideoStorageKey(),
                t.getVideoMimeType(),
                t.getVideoSizeBytes(),
                t.getDurationMs(),
                deriveThumbnailUrl(t),
                t.getActionKey(),
                t.getSortOrder(),
                t.getIsPublished(),
                t.getCreatedBy(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }

    private String deriveThumbnailUrl(Tutorial t) {
        if (t.getVideoStorageKey() == null || t.getVideoStorageKey().isBlank()) {
            return null;
        }
        String url = mediaStorage.tutorialThumbnailUrl(t.getVideoStorageKey(), t.getDurationMs());
        return (url == null || url.isBlank()) ? null : url;
    }

    private String validateDirectPublicIdForSession(String rawPublicId, String rawSessionId) {
        String cleanPublicId = rawPublicId == null ? "" : rawPublicId.strip();
        String cleanSessionId = rawSessionId == null ? "" : rawSessionId.strip();
        MediaStorage.TutorialUploadSignature expectedSig;
        try {
            expectedSig = mediaStorage.generateTutorialUploadSignature(cleanSessionId);
        } catch (MediaStorageNotConfiguredException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ lưu trữ video Cloudinary chưa được cấu hình.");
        }
        String expectedPublicId = expectedSig.folder() + "/" + expectedSig.publicId();
        if (!cleanPublicId.equals(expectedPublicId)) {
            throw ApiException.badRequest("Public ID video không khớp với phiên tải lên (upload session) hợp lệ.");
        }
        return cleanPublicId;
    }

    private ValidatedVideoResource verifyDirectVideoResource(
            String cleanPublicId,
            String clientSecureUrl,
            Long clientVideoSizeBytes,
            Long clientDurationMs
    ) {
        MediaStorage.VideoResource resource;
        try {
            resource = mediaStorage.getVideoResource(cleanPublicId);
        } catch (MediaStorageNotConfiguredException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ lưu trữ video Cloudinary chưa được cấu hình.");
        } catch (MediaStorageException e) {
            throw ApiException.badRequest("Không thể xác thực tệp video trên Cloudinary. Vui lòng tải lại video.");
        }

        String fmt = resource.format() == null ? "" : resource.format().toLowerCase(Locale.ROOT);
        if (!ALLOWED_VIDEO_FORMATS.contains(fmt)) {
            safeDestroyVideo(cleanPublicId);
            throw ApiException.badRequest("Định dạng video không được hỗ trợ. Chỉ chấp nhận tệp video MP4 (video/mp4).");
        }

        long sizeBytes = (resource.bytes() != null && resource.bytes() > 0)
                ? resource.bytes()
                : (clientVideoSizeBytes != null ? clientVideoSizeBytes : 0L);
        if (sizeBytes <= 0) {
            safeDestroyVideo(cleanPublicId);
            throw ApiException.badRequest("Tệp video không được rỗng.");
        }
        if (sizeBytes > MAX_TUTORIAL_VIDEO_SIZE_BYTES) {
            safeDestroyVideo(cleanPublicId);
            throw ApiException.badRequest(
                    "Dung lượng video vượt quá giới hạn (" + (MAX_TUTORIAL_VIDEO_SIZE_BYTES / (1024 * 1024)) + " MB)."
            );
        }

        Long durationMs = resolveDurationMs(resource.durationSeconds(), clientDurationMs);
        if (durationMs != null && durationMs <= 0) {
            safeDestroyVideo(cleanPublicId);
            throw ApiException.badRequest("Thời lượng video không hợp lệ (phải > 0 ms).");
        }

        String rawUrl = firstNonBlank(
                resource.secureUrl(),
                clientSecureUrl,
                mediaStorage.deliveryUrl(cleanPublicId, "video", "")
        );
        if (rawUrl.isBlank()) {
            safeDestroyVideo(cleanPublicId);
            throw ApiException.badRequest("Không xác định được đường dẫn video hợp lệ.");
        }
        String deliveryUrl = resolveVideoDeliveryUrl(rawUrl, cleanPublicId);

        return new ValidatedVideoResource(deliveryUrl, cleanPublicId, sizeBytes, durationMs);
    }

    private MediaStorage.UploadedAsset uploadMultipartVideo(byte[] videoBytes, String filename) {
        try {
            return mediaStorage.uploadTutorialVideo(videoBytes, filename);
        } catch (MediaStorageNotConfiguredException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ lưu trữ video Cloudinary chưa được cấu hình.");
        } catch (MediaStorageException e) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Tải video lên máy chủ lưu trữ thất bại hoặc quá thời gian xử lý. Vui lòng thử lại."
            );
        }
    }

    private byte[] readAndValidateMultipartVideo(MultipartFile videoFile) {
        if (videoFile == null) {
            throw ApiException.badRequest("Tệp video MP4 là bắt buộc.");
        }
        String contentType = videoFile.getContentType() == null ? "" : videoFile.getContentType().toLowerCase(Locale.ROOT);
        String filename = videoFile.getOriginalFilename() == null ? "" : videoFile.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!ALLOWED_VIDEO_MIMES.contains(contentType) && !filename.endsWith(".mp4")) {
            throw ApiException.badRequest("Định dạng video không được hỗ trợ. Chỉ chấp nhận tệp video MP4 (video/mp4).");
        }

        byte[] videoBytes;
        try {
            videoBytes = videoFile.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("Không thể đọc tệp video tải lên.");
        }

        if (videoBytes.length == 0) {
            throw ApiException.badRequest("Tệp video không được rỗng.");
        }
        if (videoBytes.length > MAX_TUTORIAL_VIDEO_SIZE_BYTES) {
            throw ApiException.badRequest(
                    "Dung lượng video vượt quá giới hạn cho phép (" + (MAX_TUTORIAL_VIDEO_SIZE_BYTES / (1024 * 1024)) + " MB)."
            );
        }
        return videoBytes;
    }

    private String resolveVideoDeliveryUrl(String rawUrl, String publicId) {
        String candidate = (rawUrl != null && !rawUrl.isBlank())
                ? rawUrl
                : mediaStorage.deliveryUrl(publicId, "video", "");
        String transformed = mediaStorage.tutorialVideoDeliveryUrl(candidate);
        return (transformed != null && !transformed.isBlank()) ? transformed : candidate;
    }

    private static Long resolveDurationMs(Double durationSeconds, Long fallbackMs) {
        if (durationSeconds != null) {
            return Math.round(durationSeconds * 1000.0);
        }
        return fallbackMs;
    }

    private static String validateFeatureKey(String rawFeatureKey, HttpStatus errorStatus) {
        String trimmed = rawFeatureKey == null ? "" : rawFeatureKey.strip();
        if (!ALLOWED_FEATURE_KEYS.contains(trimmed)) {
            List<String> sorted = new ArrayList<>(ALLOWED_FEATURE_KEYS);
            Collections.sort(sorted);
            throw new ApiException(
                    errorStatus,
                    "Tính năng '" + trimmed + "' không hợp lệ. Cho phép: " + String.join(", ", sorted)
            );
        }
        return trimmed;
    }

    private static String validateTitle(String rawTitle, HttpStatus errorStatus) {
        String trimmed = rawTitle == null ? "" : rawTitle.strip();
        if (trimmed.isEmpty()) {
            throw new ApiException(errorStatus, "Tiêu đề Tutorial không được để trống");
        }
        if (trimmed.codePointCount(0, trimmed.length()) > 120) {
            throw new ApiException(errorStatus, "Tiêu đề Tutorial không được vượt quá 120 ký tự");
        }
        return trimmed;
    }

    private static String validateDescription(String rawDescription, HttpStatus errorStatus) {
        if (rawDescription == null) {
            return null;
        }
        String trimmed = rawDescription.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.codePointCount(0, trimmed.length()) > 500) {
            throw new ApiException(errorStatus, "Mô tả không được vượt quá 500 ký tự");
        }
        return trimmed;
    }

    private static String validateActionKey(String rawActionKey, HttpStatus errorStatus) {
        if (rawActionKey == null) {
            return null;
        }
        String trimmed = rawActionKey.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!ALLOWED_ACTION_KEYS.contains(trimmed)) {
            List<String> sorted = new ArrayList<>(ALLOWED_ACTION_KEYS);
            Collections.sort(sorted);
            throw new ApiException(
                    errorStatus,
                    "Hành động '" + trimmed + "' không hợp lệ. Cho phép: " + String.join(", ", sorted)
            );
        }
        return trimmed;
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c;
            }
        }
        return "";
    }

    private void safeDestroyVideo(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            mediaStorage.destroyAsset(storageKey, "video");
        } catch (RuntimeException e) {
            log.warn("Best-effort cleanup failed for tutorial video {}: {}", storageKey, e.getMessage());
        }
    }
}
