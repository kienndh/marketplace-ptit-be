package com.ptiters.marketplace.integration;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cổng lưu trữ media dùng chung (Cloudinary ở Track E). Tương ứng {@code app/integrations/cloudinary.py}.
 *
 * <p>Mọi {@code upload*} ném {@link MediaStorageNotConfiguredException} nếu chưa cấu hình (giống
 * {@code CloudinaryNotConfiguredError}) và {@link MediaStorageException} nếu upload thất bại.
 * Tệp phải được kiểm tra bằng {@code realtime.MediaValidator} TRƯỚC khi gọi upload.
 */
public interface MediaStorage {

    /** Python: dict kết quả upload (secure_url, public_id, resource_type, bytes, format, duration). */
    record UploadedAsset(String secureUrl, String publicId, String resourceType,
                         Long bytes, String format, Double durationSeconds) {
    }

    /** Python: {@code generate_tutorial_upload_signature(upload_session_id)}. */
    record TutorialUploadSignature(String cloudName, String apiKey, long timestamp, String signature,
                                   String folder, String publicId, String overwrite, String uploadSessionId) {
    }

    /** Metadata video lấy từ API storage (Python: {@code get_cloudinary_video_resource}). */
    record VideoResource(String publicId, String secureUrl, String format, Long bytes, Double durationSeconds) {
    }

    /** Python: {@code upload_image(file, "products", product_id)} → {@code .../products/product-{id}}. */
    UploadedAsset uploadProductImage(byte[] data, String originalFilename, Long productId);

    /** Python: {@code upload_order_receipt} — public_id cố định "payment-receipt", overwrite + invalidate. */
    UploadedAsset uploadOrderReceipt(byte[] data, String originalFilename, Long orderId);

    /** Python: {@code upload_report_evidence}; {@code resourceType} = "image" | "video". */
    UploadedAsset uploadReportEvidence(byte[] data, String originalFilename, Long reportId, String resourceType);

    /** Python: {@code upload_message_attachment}; kind IMAGE→image, VIDEO→video, còn lại→raw. */
    UploadedAsset uploadMessageAttachment(byte[] data, String originalFilename, Long conversationId, String kind);

    /** Python: {@code upload_feedback_attachment}; {@code resourceType} = "image" | "video". */
    UploadedAsset uploadFeedbackAttachment(byte[] data, String originalFilename, String resourceType);

    /** Python: {@code upload_community_moment_image(file, user_id)}. */
    UploadedAsset uploadCommunityMomentImage(byte[] data, String originalFilename, Long userId);

    /** Python: {@code upload_tutorial_video} (tệp lớn dùng upload_large phía triển khai thật). */
    UploadedAsset uploadTutorialVideo(byte[] data, String filename);

    /** Python: {@code generate_tutorial_upload_signature} — ký direct upload, API secret không rời backend. */
    TutorialUploadSignature generateTutorialUploadSignature(String uploadSessionId);

    /** Python: {@code get_cloudinary_video_resource(public_id)} để kiểm chứng metadata phía server. */
    VideoResource getVideoResource(String publicId);

    /** Python: {@code destroy_asset(public_id, resource_type)}; best-effort, true nếu "ok"/"not found". */
    boolean destroyAsset(String publicId, String resourceType);

    /** Python: {@code generate_delivery_url}; chưa cấu hình → {@code defaultUrl} (hoặc ""). */
    String deliveryUrl(String publicId, String resourceType, String defaultUrl);

    /** Python: {@code build_tutorial_thumbnail_url(video_storage_key, duration_ms)}. */
    String tutorialThumbnailUrl(String videoStorageKey, Long durationMs);

    // ----------------------------------------------------------- URL helpers (thuần chuỗi, không cần SDK)

    /** Python: {@code get_thumbnail_url} — 200px, f_auto,q_auto. */
    default String thumbnailUrl(String imageUrl) {
        return imageUrl == null || imageUrl.isEmpty() ? null : MediaUrls.buildTransformedUrl(imageUrl, 200);
    }

    /** Python: {@code get_detail_image_url} — 800px. */
    default String detailImageUrl(String imageUrl) {
        return imageUrl == null || imageUrl.isEmpty() ? null : MediaUrls.buildTransformedUrl(imageUrl, 800);
    }

    /** Python: {@code get_hero_image_url} — 1600px. */
    default String heroImageUrl(String imageUrl) {
        return imageUrl == null || imageUrl.isEmpty() ? null : MediaUrls.buildTransformedUrl(imageUrl, 1600);
    }

    /** Python: {@code get_tutorial_video_delivery_url} — chèn vc_auto cho video Cloudinary. */
    default String tutorialVideoDeliveryUrl(String videoUrl) {
        return MediaUrls.tutorialVideoDeliveryUrl(videoUrl);
    }

    /** Hàm tĩnh dùng chung, khớp regex của Python. */
    final class MediaUrls {
        private static final Pattern IMAGE_PATTERN =
                Pattern.compile("^(https?://res\\.cloudinary\\.com/[^/]+/image/upload/)(?:[^/]+/)?(.*)$");
        private static final Pattern VIDEO_PATTERN =
                Pattern.compile("^(https?://res\\.cloudinary\\.com/[^/]+/video/upload/)(?:[^/]+/)?(v[0-9]+/.+|\\w+/.+)$");

        private MediaUrls() {
        }

        /** Python: {@code build_transformed_url(original_url, width, "auto", "auto")}; URL lạ giữ nguyên. */
        public static String buildTransformedUrl(String originalUrl, int width) {
            if (originalUrl == null || originalUrl.isEmpty()) {
                return "";
            }
            Matcher m = IMAGE_PATTERN.matcher(originalUrl);
            if (m.matches()) {
                return m.group(1) + "f_auto,q_auto,w_" + width + "/" + m.group(2);
            }
            return originalUrl;
        }

        public static String tutorialVideoDeliveryUrl(String videoUrl) {
            if (videoUrl == null || videoUrl.isEmpty()) {
                return "";
            }
            Matcher m = VIDEO_PATTERN.matcher(videoUrl);
            if (m.matches() && !videoUrl.contains("vc_")) {
                return m.group(1) + "vc_auto/" + m.group(2);
            }
            return videoUrl;
        }
    }
}
