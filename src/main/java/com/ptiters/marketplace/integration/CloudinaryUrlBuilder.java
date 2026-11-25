package com.ptiters.marketplace.integration;

import com.cloudinary.Cloudinary;
import com.cloudinary.Configuration;
import com.cloudinary.Transformation;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Trình dựng URL và chữ ký Cloudinary thuần chuỗi / SDK cục bộ (không gọi mạng),
 * giữ tương thích 100% với {@code app/integrations/cloudinary.py}.
 */
public class CloudinaryUrlBuilder {

    public static final String DEFAULT_ROOT_FOLDER = "student-marketplace";

    private final Cloudinary cloudinary;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String rootFolder;

    public CloudinaryUrlBuilder(
            String cloudName,
            String apiKey,
            String apiSecret,
            String configuredRootFolder,
            String environmentName) {
        this.cloudName = safeTrim(cloudName);
        this.apiKey = safeTrim(apiKey);
        this.apiSecret = safeTrim(apiSecret);
        this.rootFolder = resolveRootFolder(configuredRootFolder, environmentName);

        Configuration cfg = new Configuration();
        cfg.cloudName = this.cloudName;
        cfg.apiKey = this.apiKey;
        cfg.apiSecret = this.apiSecret;
        cfg.secure = true;
        this.cloudinary = new Cloudinary(cfg);
    }

    public CloudinaryUrlBuilder(Cloudinary cloudinary, String configuredRootFolder, String environmentName) {
        this.cloudinary = cloudinary;
        this.cloudName = cloudinary != null && cloudinary.config != null ? safeTrim(cloudinary.config.cloudName) : "";
        this.apiKey = cloudinary != null && cloudinary.config != null ? safeTrim(cloudinary.config.apiKey) : "";
        this.apiSecret = cloudinary != null && cloudinary.config != null ? safeTrim(cloudinary.config.apiSecret) : "";
        this.rootFolder = resolveRootFolder(configuredRootFolder, environmentName);
    }

    public static CloudinaryUrlBuilder fromConfiguration(
            String cloudinaryUrl,
            String cloudName,
            String apiKey,
            String apiSecret,
            String configuredRootFolder,
            String environmentName) {
        String resolvedCloud = safeTrim(cloudName);
        String resolvedKey = safeTrim(apiKey);
        String resolvedSecret = safeTrim(apiSecret);

        if ((resolvedCloud.isEmpty() || resolvedKey.isEmpty() || resolvedSecret.isEmpty())
                && cloudinaryUrl != null && !cloudinaryUrl.isBlank()) {
            Configuration parsed = Configuration.from(cloudinaryUrl.trim());
            if (resolvedCloud.isEmpty()) {
                resolvedCloud = safeTrim(parsed.cloudName);
            }
            if (resolvedKey.isEmpty()) {
                resolvedKey = safeTrim(parsed.apiKey);
            }
            if (resolvedSecret.isEmpty()) {
                resolvedSecret = safeTrim(parsed.apiSecret);
            }
        }
        return new CloudinaryUrlBuilder(resolvedCloud, resolvedKey, resolvedSecret, configuredRootFolder, environmentName);
    }

    public Cloudinary getCloudinary() {
        return cloudinary;
    }

    public boolean isConfigured() {
        return !cloudName.isEmpty() && !apiKey.isEmpty() && !apiSecret.isEmpty();
    }

    public String getRootFolder() {
        return rootFolder;
    }

    /**
     * Phân giải namespace thư mục gốc theo môi trường ({@code dev} vs {@code prod}),
     * khớp {@code get_cloudinary_root_folder()} trong Python.
     */
    public static String resolveRootFolder(String configuredRootFolder, String environmentName) {
        String rawRoot = configuredRootFolder == null ? "" : stripSlashes(configuredRootFolder);
        if (rawRoot.isEmpty()) {
            rawRoot = DEFAULT_ROOT_FOLDER;
        }
        if (rawRoot.endsWith("/dev") || rawRoot.endsWith("/prod")) {
            return rawRoot;
        }
        String env = environmentName == null ? "development" : environmentName.trim().toLowerCase(Locale.ROOT);
        String envSegment = ("production".equals(env) || "prod".equals(env)) ? "prod" : "dev";
        return rawRoot + "/" + envSegment;
    }

    public String productsBaseFolder() {
        return rootFolder + "/products";
    }

    public String productFolder(Long productId) {
        return productId != null ? productsBaseFolder() + "/product-" + productId : productsBaseFolder();
    }

    public String orderReceiptFolder(Long orderId) {
        return rootFolder + "/orders/order-" + orderId;
    }

    public String reportEvidenceFolder(Long reportId) {
        return rootFolder + "/reports/report-" + reportId;
    }

    public String conversationAttachmentFolder(Long conversationId) {
        return rootFolder + "/conversations/conv-" + conversationId;
    }

    public String feedbackFolder() {
        return rootFolder + "/feedback";
    }

    public String communityMomentFolder() {
        return rootFolder + "/community-moments";
    }

    public String tutorialVideosFolder() {
        return rootFolder + "/tutorials/videos";
    }

    public MediaStorage.TutorialUploadSignature generateTutorialUploadSignature(String uploadSessionId) {
        return generateTutorialUploadSignature(uploadSessionId, Instant.now().getEpochSecond());
    }

    public MediaStorage.TutorialUploadSignature generateTutorialUploadSignature(
            String uploadSessionId,
            long timestampSeconds) {
        if (!isConfigured()) {
            throw new MediaStorageNotConfiguredException();
        }
        String cleanSessionId = uploadSessionId == null ? "" : uploadSessionId.trim();
        String folder = tutorialVideosFolder();
        String leafPublicId = "tut_" + cleanSessionId;

        Map<String, Object> paramsToSign = new LinkedHashMap<>();
        paramsToSign.put("folder", folder);
        paramsToSign.put("overwrite", "false");
        paramsToSign.put("public_id", leafPublicId);
        paramsToSign.put("timestamp", timestampSeconds);

        String signature = cloudinary.apiSignRequest(paramsToSign, apiSecret, 1);
        return new MediaStorage.TutorialUploadSignature(
                cloudName,
                apiKey,
                timestampSeconds,
                signature,
                folder,
                leafPublicId,
                "false",
                cleanSessionId
        );
    }

    public String signedDeliveryUrl(String publicId, String resourceType, String defaultUrl) {
        String fallback = defaultUrl == null ? "" : defaultUrl;
        if (publicId == null || publicId.isBlank() || !isConfigured()) {
            return fallback;
        }
        String resType = (resourceType == null || resourceType.isBlank()) ? "image" : resourceType.trim();
        try {
            return cloudinary.url()
                    .resourceType(resType)
                    .secure(true)
                    .signed(true)
                    .generate(publicId.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public String tutorialThumbnailUrl(String videoStorageKey, Long durationMs) {
        if (videoStorageKey == null || videoStorageKey.isBlank() || cloudName.isEmpty()) {
            return "";
        }
        String startOffset = (durationMs != null && durationMs > 0 && durationMs < 2000L) ? "0.5" : "1";
        return cloudinary.url()
                .resourceType("video")
                .format("jpg")
                .secure(true)
                .transformation(new Transformation<>()
                        .startOffset(startOffset)
                        .width(360)
                        .crop("limit")
                        .quality("auto")
                        .fetchFormat("auto"))
                .generate(videoStorageKey.trim());
    }

    public static String buildTransformedUrl(String originalUrl, int width) {
        return MediaStorage.MediaUrls.buildTransformedUrl(originalUrl, width);
    }

    public static String thumbnailUrl(String imageUrl) {
        return imageUrl == null || imageUrl.isEmpty() ? null : buildTransformedUrl(imageUrl, 200);
    }

    public static String detailImageUrl(String imageUrl) {
        return imageUrl == null || imageUrl.isEmpty() ? null : buildTransformedUrl(imageUrl, 800);
    }

    public static String heroImageUrl(String imageUrl) {
        return imageUrl == null || imageUrl.isEmpty() ? null : buildTransformedUrl(imageUrl, 1600);
    }

    public static String tutorialVideoDeliveryUrl(String videoUrl) {
        return MediaStorage.MediaUrls.tutorialVideoDeliveryUrl(videoUrl);
    }

    private static String stripSlashes(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '/') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(start, end);
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
