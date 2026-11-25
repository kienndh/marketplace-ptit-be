package com.ptiters.marketplace.integration;

/**
 * Bản mặc định khi không có credentials: giống Python khi {@code is_cloudinary_configured() == False}.
 * Upload ném {@link MediaStorageNotConfiguredException}; destroy trả false; URL helper giữ nguyên chuỗi.
 */
public class UnconfiguredMediaStorage implements MediaStorage {

    private static MediaStorageNotConfiguredException off() {
        return new MediaStorageNotConfiguredException();
    }

    @Override
    public UploadedAsset uploadProductImage(byte[] data, String originalFilename, Long productId) {
        throw off();
    }

    @Override
    public UploadedAsset uploadOrderReceipt(byte[] data, String originalFilename, Long orderId) {
        throw off();
    }

    @Override
    public UploadedAsset uploadReportEvidence(byte[] data, String originalFilename, Long reportId, String resourceType) {
        throw off();
    }

    @Override
    public UploadedAsset uploadMessageAttachment(byte[] data, String originalFilename, Long conversationId, String kind) {
        throw off();
    }

    @Override
    public UploadedAsset uploadFeedbackAttachment(byte[] data, String originalFilename, String resourceType) {
        throw off();
    }

    @Override
    public UploadedAsset uploadCommunityMomentImage(byte[] data, String originalFilename, Long userId) {
        throw off();
    }

    @Override
    public UploadedAsset uploadTutorialVideo(byte[] data, String filename) {
        throw off();
    }

    @Override
    public TutorialUploadSignature generateTutorialUploadSignature(String uploadSessionId) {
        throw off();
    }

    @Override
    public VideoResource getVideoResource(String publicId) {
        throw off();
    }

    @Override
    public boolean destroyAsset(String publicId, String resourceType) {
        return false;
    }

    @Override
    public String deliveryUrl(String publicId, String resourceType, String defaultUrl) {
        return defaultUrl == null ? "" : defaultUrl;
    }

    @Override
    public String tutorialThumbnailUrl(String videoStorageKey, Long durationMs) {
        return "";
    }
}
