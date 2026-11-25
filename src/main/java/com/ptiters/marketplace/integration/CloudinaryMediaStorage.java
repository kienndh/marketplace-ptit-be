package com.ptiters.marketplace.integration;

import com.cloudinary.Cloudinary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Triển khai {@link MediaStorage} trên Cloudinary thật (tương ứng {@code app/integrations/cloudinary.py}).
 * Chỉ kích hoạt khi cấu hình {@code CLOUDINARY_URL} hoặc đủ bộ {@code CLOUDINARY_CLOUD_NAME},
 * {@code CLOUDINARY_API_KEY}, {@code CLOUDINARY_API_SECRET}.
 */
@Service
@ConditionalOnExpression(
        "!'${CLOUDINARY_URL:}'.isBlank() "
        + "|| (!'${CLOUDINARY_CLOUD_NAME:}'.isBlank() && !'${CLOUDINARY_API_KEY:}'.isBlank() && !'${CLOUDINARY_API_SECRET:}'.isBlank())"
)
public class CloudinaryMediaStorage implements MediaStorage {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryMediaStorage.class);
    private static final long UPLOAD_LARGE_THRESHOLD_BYTES = 20L * 1024L * 1024L;
    private static final int UPLOAD_LARGE_CHUNK_BYTES = 20 * 1024 * 1024;

    private final Cloudinary cloudinary;
    private final CloudinaryUrlBuilder urlBuilder;

    @Autowired
    public CloudinaryMediaStorage(
            @Value("${CLOUDINARY_URL:}") String cloudinaryUrl,
            @Value("${CLOUDINARY_CLOUD_NAME:}") String cloudName,
            @Value("${CLOUDINARY_API_KEY:}") String apiKey,
            @Value("${CLOUDINARY_API_SECRET:}") String apiSecret,
            @Value("${CLOUDINARY_ROOT_FOLDER:student-marketplace}") String rootFolder,
            @Value("${ENVIRONMENT:}") String configuredEnvironment,
            Environment springEnvironment) {
        String resolvedEnv = resolveEnvironmentName(configuredEnvironment, springEnvironment);
        this.urlBuilder = CloudinaryUrlBuilder.fromConfiguration(
                cloudinaryUrl, cloudName, apiKey, apiSecret, rootFolder, resolvedEnv);
        this.cloudinary = this.urlBuilder.getCloudinary();
        if (this.urlBuilder.isConfigured()) {
            log.info("CloudinaryMediaStorage configured for root folder '{}'.", this.urlBuilder.getRootFolder());
        } else {
            log.warn("CloudinaryMediaStorage activated with incomplete credentials.");
        }
    }

    CloudinaryMediaStorage(Cloudinary cloudinary, CloudinaryUrlBuilder urlBuilder) {
        this.cloudinary = cloudinary;
        this.urlBuilder = urlBuilder;
    }

    @Override
    public UploadedAsset uploadProductImage(byte[] data, String originalFilename, Long productId) {
        requireConfigured();
        Map<String, Object> options = new HashMap<>();
        options.put("folder", urlBuilder.productFolder(productId));
        options.put("resource_type", "image");
        putFilenameIfPresent(options, originalFilename);
        return doUpload(data, options, "image", "Cloudinary upload failed");
    }

    @Override
    public UploadedAsset uploadOrderReceipt(byte[] data, String originalFilename, Long orderId) {
        requireConfigured();
        Map<String, Object> options = new HashMap<>();
        options.put("folder", urlBuilder.orderReceiptFolder(orderId));
        options.put("public_id", "payment-receipt");
        options.put("overwrite", true);
        options.put("invalidate", true);
        options.put("resource_type", "image");
        putFilenameIfPresent(options, originalFilename);
        return doUpload(data, options, "image", "Cloudinary receipt upload failed");
    }

    @Override
    public UploadedAsset uploadReportEvidence(byte[] data, String originalFilename, Long reportId, String resourceType) {
        requireConfigured();
        String resType = normalizeResourceType(resourceType, "image");
        Map<String, Object> options = new HashMap<>();
        options.put("folder", urlBuilder.reportEvidenceFolder(reportId));
        options.put("resource_type", resType);
        options.put("use_filename", true);
        options.put("unique_filename", true);
        putFilenameWithFallback(options, originalFilename, "evidence");
        return doUpload(data, options, resType, "Cloudinary report evidence upload failed");
    }

    @Override
    public UploadedAsset uploadMessageAttachment(byte[] data, String originalFilename, Long conversationId, String kind) {
        requireConfigured();
        String resType = mapMessageKindToResourceType(kind);
        Map<String, Object> options = new HashMap<>();
        options.put("folder", urlBuilder.conversationAttachmentFolder(conversationId));
        options.put("resource_type", resType);
        options.put("use_filename", true);
        options.put("unique_filename", true);
        putFilenameWithFallback(options, originalFilename, "attachment");
        return doUpload(data, options, resType, "Cloudinary attachment upload failed");
    }

    @Override
    public UploadedAsset uploadFeedbackAttachment(byte[] data, String originalFilename, String resourceType) {
        requireConfigured();
        String resType = normalizeResourceType(resourceType, "image");
        Map<String, Object> options = new HashMap<>();
        options.put("folder", urlBuilder.feedbackFolder());
        options.put("resource_type", resType);
        options.put("unique_filename", true);
        putFilenameIfPresent(options, originalFilename);
        return doUpload(data, options, resType, "Cloudinary feedback attachment upload failed");
    }

    @Override
    public UploadedAsset uploadCommunityMomentImage(byte[] data, String originalFilename, Long userId) {
        requireConfigured();
        Map<String, Object> options = new HashMap<>();
        options.put("folder", urlBuilder.communityMomentFolder());
        options.put("resource_type", "image");
        options.put("unique_filename", true);
        putFilenameIfPresent(options, originalFilename);
        return doUpload(data, options, "image", "Cloudinary community moment upload failed");
    }

    @Override
    public UploadedAsset uploadTutorialVideo(byte[] data, String filename) {
        requireConfigured();
        if (data == null || data.length == 0) {
            throw new MediaStorageException("Cloudinary tutorial video upload failed: empty data");
        }
        String safeFilename = (filename == null || filename.isBlank()) ? "tutorial.mp4" : filename.trim();
        Map<String, Object> options = new HashMap<>();
        options.put("folder", urlBuilder.tutorialVideosFolder());
        options.put("resource_type", "video");
        options.put("unique_filename", true);
        options.put("filename", safeFilename);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = data.length > UPLOAD_LARGE_THRESHOLD_BYTES
                    ? cloudinary.uploader().uploadLarge(data, withChunkSize(options))
                    : cloudinary.uploader().upload(data, options);

            if (result == null || result.isEmpty()) {
                throw new MediaStorageException("Cloudinary returned an invalid or empty response.");
            }
            String returnedResType = asString(result.get("resource_type"));
            String publicId = asString(result.get("public_id"));
            if (!"video".equals(returnedResType)) {
                if (publicId != null && !publicId.isBlank()) {
                    destroyAsset(publicId, returnedResType != null ? returnedResType : "raw");
                }
                throw new MediaStorageException("Cloudinary upload returned unexpected resource_type: " + returnedResType);
            }
            String rawUrl = firstNonBlank(asString(result.get("secure_url")), asString(result.get("url")));
            if (rawUrl == null) {
                throw new MediaStorageException("Cloudinary upload did not return a valid delivery URL.");
            }
            String deliveryUrl = tutorialVideoDeliveryUrl(rawUrl);
            return new UploadedAsset(
                    deliveryUrl,
                    publicId,
                    returnedResType,
                    asLong(result.get("bytes"), (long) data.length),
                    asString(result.get("format")),
                    asDouble(result.get("duration"))
            );
        } catch (MediaStorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to upload tutorial video to Cloudinary: {}", e.getMessage());
            throw new MediaStorageException("Cloudinary tutorial video upload failed: " + e.getMessage(), e);
        }
    }

    @Override
    public TutorialUploadSignature generateTutorialUploadSignature(String uploadSessionId) {
        requireConfigured();
        return urlBuilder.generateTutorialUploadSignature(uploadSessionId);
    }

    @Override
    public VideoResource getVideoResource(String publicId) {
        requireConfigured();
        if (publicId == null || publicId.isBlank()) {
            throw new MediaStorageException("Kiểm chứng tài nguyên video Cloudinary thất bại: public_id rỗng.");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resource = cloudinary.api().resource(publicId.trim(), Map.of("resource_type", "video"));
            if (resource == null || resource.isEmpty()) {
                throw new MediaStorageException("Không tìm thấy tài nguyên Cloudinary với public_id: " + publicId);
            }
            String resPublicId = firstNonBlank(asString(resource.get("public_id")), publicId.trim());
            String secureUrl = firstNonBlank(asString(resource.get("secure_url")), asString(resource.get("url")));
            return new VideoResource(
                    resPublicId,
                    secureUrl,
                    asString(resource.get("format")),
                    asLong(resource.get("bytes"), null),
                    asDouble(resource.get("duration"))
            );
        } catch (MediaStorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch Cloudinary video resource {}: {}", publicId, e.getMessage());
            throw new MediaStorageException("Kiểm chứng tài nguyên video Cloudinary thất bại: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean destroyAsset(String publicId, String resourceType) {
        if (!urlBuilder.isConfigured() || publicId == null || publicId.isBlank()) {
            return false;
        }
        String resType = normalizeResourceType(resourceType, "image");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().destroy(publicId.trim(), Map.of("resource_type", resType));
            Object status = result != null ? result.get("result") : null;
            if ("ok".equals(status) || "not found".equals(status)) {
                return true;
            }
            log.warn("Cloudinary destroy returned non-ok result for {} ({}): {}", publicId, resType, result);
            return false;
        } catch (Exception e) {
            log.error("Error executing Cloudinary destroy for {} ({}): {}", publicId, resType, e.getMessage());
            return false;
        }
    }

    @Override
    public String deliveryUrl(String publicId, String resourceType, String defaultUrl) {
        return urlBuilder.signedDeliveryUrl(publicId, resourceType, defaultUrl);
    }

    @Override
    public String tutorialThumbnailUrl(String videoStorageKey, Long durationMs) {
        return urlBuilder.tutorialThumbnailUrl(videoStorageKey, durationMs);
    }

    private UploadedAsset doUpload(byte[] data, Map<String, Object> options, String fallbackType, String errorPrefix) {
        if (data == null || data.length == 0) {
            throw new MediaStorageException(errorPrefix + ": empty file data");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(data, options);
            if (result == null || result.isEmpty()) {
                throw new MediaStorageException(errorPrefix + ": empty Cloudinary response");
            }
            String secureUrl = firstNonBlank(asString(result.get("secure_url")), asString(result.get("url")));
            String publicId = asString(result.get("public_id"));
            String resourceType = firstNonBlank(asString(result.get("resource_type")), fallbackType);
            return new UploadedAsset(
                    secureUrl,
                    publicId,
                    resourceType,
                    asLong(result.get("bytes"), (long) data.length),
                    asString(result.get("format")),
                    asDouble(result.get("duration"))
            );
        } catch (MediaStorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("{}: {}", errorPrefix, e.getMessage());
            throw new MediaStorageException(errorPrefix + ": " + e.getMessage(), e);
        }
    }

    private void requireConfigured() {
        if (!urlBuilder.isConfigured()) {
            throw new MediaStorageNotConfiguredException();
        }
    }

    private static Map<String, Object> withChunkSize(Map<String, Object> baseOptions) {
        Map<String, Object> copy = new HashMap<>(baseOptions);
        copy.put("chunk_size", UPLOAD_LARGE_CHUNK_BYTES);
        return copy;
    }

    private static String resolveEnvironmentName(String configuredEnvironment, Environment springEnvironment) {
        if (configuredEnvironment != null && !configuredEnvironment.isBlank()) {
            return configuredEnvironment.trim();
        }
        if (springEnvironment != null && springEnvironment.acceptsProfiles(Profiles.of("production", "prod"))) {
            return "production";
        }
        return "development";
    }

    static String mapMessageKindToResourceType(String kind) {
        if (kind == null) {
            return "raw";
        }
        return switch (kind.trim().toUpperCase(Locale.ROOT)) {
            case "IMAGE" -> "image";
            case "VIDEO", "AUDIO" -> "video";
            default -> "raw";
        };
    }

    private static String normalizeResourceType(String resourceType, String fallback) {
        return (resourceType == null || resourceType.isBlank())
                ? fallback
                : resourceType.trim().toLowerCase(Locale.ROOT);
    }

    private static void putFilenameIfPresent(Map<String, Object> options, String originalFilename) {
        if (originalFilename != null && !originalFilename.isBlank()) {
            options.put("filename", originalFilename.trim());
        }
    }

    private static void putFilenameWithFallback(Map<String, Object> options, String originalFilename, String fallback) {
        String name = (originalFilename == null || originalFilename.isBlank()) ? fallback : originalFilename.trim();
        options.put("filename", name);
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return (second != null && !second.isBlank()) ? second : null;
    }

    private static Long asLong(Object value, Long fallback) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return fallback;
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }
}
