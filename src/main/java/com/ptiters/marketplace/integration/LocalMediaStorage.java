package com.ptiters.marketplace.integration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/**
 * Bản lưu trữ cục bộ cho profile {@code test}: ghi tệp vào thư mục tạm, trả URL giả lập
 * {@code https://media.local.test/<public_id>}. Không gọi mạng. Cấu trúc folder giống Cloudinary.
 */
public class LocalMediaStorage implements MediaStorage {

    public static final String BASE_URL = "https://media.local.test/";
    private static final String ROOT = "student-marketplace/test";

    private final Path baseDir;
    private final String baseUrl;

    public LocalMediaStorage(Path baseDir) {
        this(baseDir, BASE_URL);
    }

    /** Dùng cho profile {@code e2e}: URL trả về trỏ tới đường dẫn tĩnh của chính Java để FE hiển thị được. */
    public LocalMediaStorage(Path baseDir, String baseUrl) {
        this.baseDir = baseDir;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    public static LocalMediaStorage inTempDirectory() {
        try {
            return new LocalMediaStorage(Files.createTempDirectory("marketplace-local-media-"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path getBaseDir() {
        return baseDir;
    }

    @Override
    public UploadedAsset uploadProductImage(byte[] data, String originalFilename, Long productId) {
        return store(data, ROOT + "/products/product-" + productId, "image", originalFilename);
    }

    @Override
    public UploadedAsset uploadOrderReceipt(byte[] data, String originalFilename, Long orderId) {
        return store(data, ROOT + "/orders/order-" + orderId, "image", "payment-receipt", true);
    }

    @Override
    public UploadedAsset uploadReportEvidence(byte[] data, String originalFilename, Long reportId, String resourceType) {
        return store(data, ROOT + "/reports/report-" + reportId, resourceType, originalFilename);
    }

    @Override
    public UploadedAsset uploadMessageAttachment(byte[] data, String originalFilename, Long conversationId, String kind) {
        String type = switch (kind == null ? "" : kind.toUpperCase(Locale.ROOT)) {
            case "IMAGE" -> "image";
            case "VIDEO" -> "video";
            default -> "raw";
        };
        return store(data, ROOT + "/conversations/conv-" + conversationId, type, originalFilename);
    }

    @Override
    public UploadedAsset uploadFeedbackAttachment(byte[] data, String originalFilename, String resourceType) {
        return store(data, ROOT + "/feedback", resourceType, originalFilename);
    }

    @Override
    public UploadedAsset uploadCommunityMomentImage(byte[] data, String originalFilename, Long userId) {
        return store(data, ROOT + "/community-moments", "image", originalFilename);
    }

    @Override
    public UploadedAsset uploadTutorialVideo(byte[] data, String filename) {
        return store(data, ROOT + "/tutorials/videos", "video", filename);
    }

    @Override
    public TutorialUploadSignature generateTutorialUploadSignature(String uploadSessionId) {
        return new TutorialUploadSignature("local-test", "local-key", System.currentTimeMillis() / 1000,
                "local-signature", ROOT + "/tutorials/videos", "tut_" + uploadSessionId, "false", uploadSessionId);
    }

    @Override
    public VideoResource getVideoResource(String publicId) {
        Path file = baseDir.resolve(publicId);
        if (!Files.exists(file)) {
            throw new MediaStorageException("Không tìm thấy tài nguyên với public_id: " + publicId);
        }
        try {
            return new VideoResource(publicId, baseUrl + publicId, "mp4", Files.size(file), null);
        } catch (IOException e) {
            throw new MediaStorageException("Kiểm chứng tài nguyên thất bại: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean destroyAsset(String publicId, String resourceType) {
        if (publicId == null || publicId.isEmpty()) {
            return false;
        }
        try {
            Files.deleteIfExists(baseDir.resolve(publicId));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String deliveryUrl(String publicId, String resourceType, String defaultUrl) {
        if (publicId == null || publicId.isEmpty()) {
            return defaultUrl == null ? "" : defaultUrl;
        }
        return baseUrl + publicId;
    }

    @Override
    public String tutorialThumbnailUrl(String videoStorageKey, Long durationMs) {
        return videoStorageKey == null || videoStorageKey.isEmpty() ? "" : baseUrl + videoStorageKey + ".jpg";
    }

    private UploadedAsset store(byte[] data, String folder, String resourceType, String name) {
        return store(data, folder, resourceType, name, false);
    }

    private UploadedAsset store(byte[] data, String folder, String resourceType, String name, boolean fixedName) {
        String leaf = fixedName ? name : UUID.randomUUID().toString().replace("-", "");
        String publicId = folder + "/" + leaf;
        try {
            Path target = baseDir.resolve(publicId);
            Files.createDirectories(target.getParent());
            Files.write(target, data);
        } catch (IOException e) {
            throw new MediaStorageException("Upload thất bại: " + e.getMessage(), e);
        }
        String ext = "";
        if (name != null && name.contains(".")) {
            ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        }
        return new UploadedAsset(baseUrl + publicId, publicId, resourceType, (long) data.length,
                ext.isEmpty() ? null : ext, null);
    }
}
