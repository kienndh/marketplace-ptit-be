package com.ptiters.marketplace.integration;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Facade tương thích ngược cho các module cũ (như {@code product/ProductService}),
 * uỷ quyền toàn bộ thao tác lưu trữ media cho bean {@link MediaStorage}.
 */
@Service
public class CloudinaryStorageService {

    private static final Pattern PRODUCT_FOLDER_PATTERN = Pattern.compile("product-(\\d+)");
    private static final Pattern ORDER_FOLDER_PATTERN = Pattern.compile("order-(\\d+)");
    private static final Pattern REPORT_FOLDER_PATTERN = Pattern.compile("report-(\\d+)");
    private static final Pattern CONV_FOLDER_PATTERN = Pattern.compile("conv-(\\d+)");

    private final MediaStorage mediaStorage;

    public CloudinaryStorageService(MediaStorage mediaStorage) {
        this.mediaStorage = mediaStorage;
    }

    public String uploadImage(byte[] data, String folder, String publicId) {
        String filename = (publicId != null && !publicId.isBlank()) ? publicId.trim() : "image.jpg";
        if (folder != null) {
            Long productId = extractTrailingId(PRODUCT_FOLDER_PATTERN, folder);
            if (productId != null) {
                return mediaStorage.uploadProductImage(data, filename, productId).secureUrl();
            }
            Long orderId = extractTrailingId(ORDER_FOLDER_PATTERN, folder);
            if (orderId != null) {
                return mediaStorage.uploadOrderReceipt(data, filename, orderId).secureUrl();
            }
            Long reportId = extractTrailingId(REPORT_FOLDER_PATTERN, folder);
            if (reportId != null) {
                return mediaStorage.uploadReportEvidence(data, filename, reportId, "image").secureUrl();
            }
            Long convId = extractTrailingId(CONV_FOLDER_PATTERN, folder);
            if (convId != null) {
                return mediaStorage.uploadMessageAttachment(data, filename, convId, "IMAGE").secureUrl();
            }
            if (folder.contains("community-moments")) {
                return mediaStorage.uploadCommunityMomentImage(data, filename, 0L).secureUrl();
            }
            if (folder.contains("feedback")) {
                return mediaStorage.uploadFeedbackAttachment(data, filename, "image").secureUrl();
            }
        }
        return mediaStorage.uploadProductImage(data, filename, 0L).secureUrl();
    }

    public String uploadVideo(byte[] data, String folder, String publicId) {
        String filename = (publicId != null && !publicId.isBlank()) ? publicId.trim() : "video.mp4";
        if (folder != null) {
            Long reportId = extractTrailingId(REPORT_FOLDER_PATTERN, folder);
            if (reportId != null) {
                return mediaStorage.uploadReportEvidence(data, filename, reportId, "video").secureUrl();
            }
            Long convId = extractTrailingId(CONV_FOLDER_PATTERN, folder);
            if (convId != null) {
                return mediaStorage.uploadMessageAttachment(data, filename, convId, "VIDEO").secureUrl();
            }
            if (folder.contains("feedback")) {
                return mediaStorage.uploadFeedbackAttachment(data, filename, "video").secureUrl();
            }
        }
        return mediaStorage.uploadTutorialVideo(data, filename).secureUrl();
    }

    public boolean destroyAsset(String publicId, String resourceType) {
        return mediaStorage.destroyAsset(publicId, resourceType);
    }

    public String generateDeliveryUrl(String publicId, String transformation) {
        if (publicId == null || publicId.isBlank()) {
            return "";
        }
        String trimmed = publicId.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            if (transformation != null) {
                if (transformation.contains("w_200")) {
                    return mediaStorage.thumbnailUrl(trimmed);
                }
                if (transformation.contains("w_800")) {
                    return mediaStorage.detailImageUrl(trimmed);
                }
                if (transformation.contains("w_1600")) {
                    return mediaStorage.heroImageUrl(trimmed);
                }
                if (transformation.contains("vc_auto")) {
                    return mediaStorage.tutorialVideoDeliveryUrl(trimmed);
                }
            }
            return trimmed;
        }
        return mediaStorage.deliveryUrl(trimmed, "image", "");
    }

    public Map<String, Object> generateUploadSignature(String folder, String publicId) {
        String sessionId;
        if (publicId != null && !publicId.isBlank()) {
            String trimmed = publicId.trim();
            sessionId = trimmed.startsWith("tut_") ? trimmed.substring(4) : trimmed;
        } else {
            sessionId = UUID.randomUUID().toString().replace("-", "");
        }

        MediaStorage.TutorialUploadSignature sig = mediaStorage.generateTutorialUploadSignature(sessionId);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("cloud_name", sig.cloudName());
        map.put("api_key", sig.apiKey());
        map.put("timestamp", sig.timestamp());
        map.put("signature", sig.signature());
        map.put("folder", sig.folder());
        map.put("public_id", sig.publicId());
        map.put("overwrite", sig.overwrite());
        map.put("upload_session_id", sig.uploadSessionId());
        return map;
    }

    private static Long extractTrailingId(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
