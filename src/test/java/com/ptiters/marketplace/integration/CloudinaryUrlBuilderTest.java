package com.ptiters.marketplace.integration;

import com.cloudinary.Api;
import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.cloudinary.api.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudinaryUrlBuilderTest {

    @Test
    @DisplayName("resolveRootFolder phân giải dev/prod và giữ nguyên hậu tố sẵn có giống Python")
    void resolveRootFolderMatchesPython() {
        assertEquals("student-marketplace/dev",
                CloudinaryUrlBuilder.resolveRootFolder("student-marketplace", "development"));
        assertEquals("student-marketplace/dev",
                CloudinaryUrlBuilder.resolveRootFolder("", "test"));
        assertEquals("student-marketplace/prod",
                CloudinaryUrlBuilder.resolveRootFolder("/student-marketplace/", "production"));
        assertEquals("student-marketplace/prod",
                CloudinaryUrlBuilder.resolveRootFolder("student-marketplace", "PROD"));
        assertEquals("custom-root/dev",
                CloudinaryUrlBuilder.resolveRootFolder("/custom-root/dev/", "production"));
        assertEquals("custom-root/prod",
                CloudinaryUrlBuilder.resolveRootFolder("custom-root/prod", "development"));
    }

    @Test
    @DisplayName("Các folder con khớp 100% cấu trúc cloudinary.py")
    void folderHierarchyMatchesPython() {
        CloudinaryUrlBuilder builder = new CloudinaryUrlBuilder(
                "demo-cloud", "key123", "secret456", "student-marketplace", "development");

        assertEquals("student-marketplace/dev/products", builder.productsBaseFolder());
        assertEquals("student-marketplace/dev/products/product-42", builder.productFolder(42L));
        assertEquals("student-marketplace/dev/orders/order-7", builder.orderReceiptFolder(7L));
        assertEquals("student-marketplace/dev/reports/report-9", builder.reportEvidenceFolder(9L));
        assertEquals("student-marketplace/dev/conversations/conv-11", builder.conversationAttachmentFolder(11L));
        assertEquals("student-marketplace/dev/feedback", builder.feedbackFolder());
        assertEquals("student-marketplace/dev/community-moments", builder.communityMomentFolder());
        assertEquals("student-marketplace/dev/tutorials/videos", builder.tutorialVideosFolder());
    }

    @Test
    @DisplayName("URL transformation (200/800/1600) và vc_auto video delivery URL khớp Python regex")
    void transformedUrlsAndVideoDeliveryMatchPython() {
        String rawImg = "https://res.cloudinary.com/demo/image/upload/v1700000000/student-marketplace/dev/book.jpg";
        assertEquals(
                "https://res.cloudinary.com/demo/image/upload/f_auto,q_auto,w_200/student-marketplace/dev/book.jpg",
                CloudinaryUrlBuilder.thumbnailUrl(rawImg));
        assertEquals(
                "https://res.cloudinary.com/demo/image/upload/f_auto,q_auto,w_800/student-marketplace/dev/book.jpg",
                CloudinaryUrlBuilder.detailImageUrl(rawImg));
        assertEquals(
                "https://res.cloudinary.com/demo/image/upload/f_auto,q_auto,w_1600/student-marketplace/dev/book.jpg",
                CloudinaryUrlBuilder.heroImageUrl(rawImg));

        String nonCloudinary = "https://media.local.test/student-marketplace/test/book.jpg";
        assertEquals(nonCloudinary, CloudinaryUrlBuilder.thumbnailUrl(nonCloudinary));
        assertNull(CloudinaryUrlBuilder.thumbnailUrl(null));

        String rawVideo = "https://res.cloudinary.com/demo/video/upload/v1700/student-marketplace/dev/tutorials/videos/t1.mp4";
        assertEquals(
                "https://res.cloudinary.com/demo/video/upload/vc_auto/v1700/student-marketplace/dev/tutorials/videos/t1.mp4",
                CloudinaryUrlBuilder.tutorialVideoDeliveryUrl(rawVideo));
        String alreadyVcAuto = "https://res.cloudinary.com/demo/video/upload/vc_auto/v1700/t1.mp4";
        assertEquals(alreadyVcAuto, CloudinaryUrlBuilder.tutorialVideoDeliveryUrl(alreadyVcAuto));
    }

    @Test
    @DisplayName("tutorialThumbnailUrl chọn frame 1s hoặc 0.5s và signedDeliveryUrl tạo chữ ký URL cục bộ")
    void tutorialThumbnailAndSignedDeliveryUrls() {
        CloudinaryUrlBuilder builder = new CloudinaryUrlBuilder(
                "demo-cloud", "key123", "secret456", "student-marketplace", "development");

        String thumbNormal = builder.tutorialThumbnailUrl("student-marketplace/dev/tutorials/videos/tut_abc", 5000L);
        assertTrue(thumbNormal.startsWith("https://res.cloudinary.com/demo-cloud/video/upload/"));
        assertTrue(thumbNormal.contains("so_1"));
        assertTrue(thumbNormal.contains("w_360"));
        assertTrue(thumbNormal.contains("c_limit"));
        assertTrue(thumbNormal.contains("f_auto"));
        assertTrue(thumbNormal.contains("q_auto"));
        assertTrue(thumbNormal.endsWith("/student-marketplace/dev/tutorials/videos/tut_abc.jpg"));

        String thumbShort = builder.tutorialThumbnailUrl("student-marketplace/dev/tutorials/videos/tut_abc", 1500L);
        assertTrue(thumbShort.contains("so_0.5"));

        String signedUrl = builder.signedDeliveryUrl("student-marketplace/dev/reports/report-1/ev1", "image", "fallback");
        assertTrue(signedUrl.startsWith("https://res.cloudinary.com/demo-cloud/image/upload/s--"));

        CloudinaryUrlBuilder unconfigured = new CloudinaryUrlBuilder("", "", "", "student-marketplace", "development");
        assertEquals("fallback", unconfigured.signedDeliveryUrl("abc", "image", "fallback"));
    }

    @Test
    @DisplayName("generateTutorialUploadSignature sinh chữ ký SHA-1 chính xác với api_sign_request của Python")
    void tutorialUploadSignatureMatchesSha1Spec() throws Exception {
        CloudinaryUrlBuilder builder = CloudinaryUrlBuilder.fromConfiguration(
                "cloudinary://myKey:mySecret@myCloud", "", "", "", "student-marketplace", "production");

        long ts = 1760000000L;
        MediaStorage.TutorialUploadSignature sig = builder.generateTutorialUploadSignature("sess99", ts);

        assertEquals("myCloud", sig.cloudName());
        assertEquals("myKey", sig.apiKey());
        assertEquals(ts, sig.timestamp());
        assertEquals("student-marketplace/prod/tutorials/videos", sig.folder());
        assertEquals("tut_sess99", sig.publicId());
        assertEquals("false", sig.overwrite());
        assertEquals("sess99", sig.uploadSessionId());

        String canonical = "folder=student-marketplace/prod/tutorials/videos&overwrite=false&public_id=tut_sess99&timestamp="
                + ts + "mySecret";
        byte[] digest = MessageDigest.getInstance("SHA-1").digest(canonical.getBytes(StandardCharsets.UTF_8));
        String expectedSig = HexFormat.of().formatHex(digest);
        assertEquals(expectedSig, sig.signature());
    }

    @Test
    @DisplayName("CloudinaryMediaStorage kích hoạt đúng điều kiện và uỷ quyền Uploader/Api không gọi mạng thật")
    void cloudinaryMediaStorageConditionAndMockedOperations() throws Exception {
        // 1. Verify @ConditionalOnExpression behavior without starting full DB context
        new ApplicationContextRunner()
                .withUserConfiguration(CloudinaryMediaStorage.class)
                .run(ctx -> assertFalse(ctx.containsBean("cloudinaryMediaStorage")));

        new ApplicationContextRunner()
                .withPropertyValues("CLOUDINARY_URL=cloudinary://k:s@c")
                .withUserConfiguration(CloudinaryMediaStorage.class)
                .run(ctx -> assertTrue(ctx.containsBean("cloudinaryMediaStorage")));

        // 2. Unit test with mocked Cloudinary Uploader & Api
        Cloudinary mockCloudinary = mock(Cloudinary.class);
        Uploader mockUploader = mock(Uploader.class);
        Api mockApi = mock(Api.class);
        when(mockCloudinary.uploader()).thenReturn(mockUploader);
        when(mockCloudinary.api()).thenReturn(mockApi);

        CloudinaryUrlBuilder urlBuilder = new CloudinaryUrlBuilder(
                "myCloud", "myKey", "mySecret", "student-marketplace", "development");
        CloudinaryMediaStorage storage = new CloudinaryMediaStorage(mockCloudinary, urlBuilder);

        when(mockUploader.upload(any(byte[].class), any(Map.class))).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/myCloud/image/upload/v1/student-marketplace/dev/products/product-5/a.jpg",
                "public_id", "student-marketplace/dev/products/product-5/a",
                "resource_type", "image",
                "bytes", 1234,
                "format", "jpg"
        ));

        MediaStorage.UploadedAsset img = storage.uploadProductImage(new byte[]{1, 2, 3, 4}, "a.jpg", 5L);
        assertEquals("student-marketplace/dev/products/product-5/a", img.publicId());
        assertEquals(1234L, img.bytes());

        // Unexpected resource_type on tutorial video triggers cleanup and exception
        when(mockUploader.upload(any(byte[].class), any(Map.class))).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/myCloud/raw/upload/v1/bad",
                "public_id", "student-marketplace/dev/tutorials/videos/bad",
                "resource_type", "raw"
        ));
        when(mockUploader.destroy(eq("student-marketplace/dev/tutorials/videos/bad"), any(Map.class)))
                .thenReturn(Map.of("result", "ok"));

        assertThrows(MediaStorageException.class,
                () -> storage.uploadTutorialVideo(new byte[]{1, 2, 3, 4}, "bad.mp4"));
        verify(mockUploader).destroy(eq("student-marketplace/dev/tutorials/videos/bad"), eq(Map.of("resource_type", "raw")));

        // Destroy returns true on "ok" or "not found", false on unexpected
        when(mockUploader.destroy(eq("k1"), any(Map.class))).thenReturn(Map.of("result", "not found"));
        assertTrue(storage.destroyAsset("k1", "image"));

        // getVideoResource reads Api.resource
        ApiResponse apiResp = mock(ApiResponse.class);
        when(apiResp.get("public_id")).thenReturn("tut_1");
        when(apiResp.get("secure_url")).thenReturn("https://res.cloudinary.com/myCloud/video/upload/v1/tut_1.mp4");
        when(apiResp.get("format")).thenReturn("mp4");
        when(apiResp.get("bytes")).thenReturn(50000);
        when(apiResp.get("duration")).thenReturn(12.5);
        when(apiResp.isEmpty()).thenReturn(false);
        when(mockApi.resource(eq("tut_1"), eq(Map.of("resource_type", "video")))).thenReturn(apiResp);

        MediaStorage.VideoResource res = storage.getVideoResource("tut_1");
        assertEquals("tut_1", res.publicId());
        assertEquals("mp4", res.format());
        assertEquals(50000L, res.bytes());
        assertEquals(12.5, res.durationSeconds());

        // Check springEnvironment fallback constructor compiles and configures
        CloudinaryMediaStorage fromProps = new CloudinaryMediaStorage(
                "cloudinary://k:s@c", "", "", "", "student-marketplace", "", new StandardEnvironment());
        assertNotNull(fromProps);
    }

    @Test
    @DisplayName("CloudinaryStorageService uỷ quyền toàn bộ cho MediaStorage mà không đổi signature")
    void cloudinaryStorageServiceDelegatesToMediaStorage() {
        MediaStorage mockStorage = Mockito.mock(MediaStorage.class);
        when(mockStorage.uploadProductImage(any(), eq("photo.jpg"), eq(12L)))
                .thenReturn(new MediaStorage.UploadedAsset("https://cdn/p12.jpg", "p12", "image", 10L, "jpg", null));
        when(mockStorage.destroyAsset("p12", "image")).thenReturn(true);
        when(mockStorage.generateTutorialUploadSignature("abc"))
                .thenReturn(new MediaStorage.TutorialUploadSignature(
                        "cloud", "key", 100L, "sig", "student-marketplace/dev/tutorials/videos", "tut_abc", "false", "abc"));

        CloudinaryStorageService service = new CloudinaryStorageService(mockStorage);
        assertEquals("https://cdn/p12.jpg",
                service.uploadImage(new byte[]{1, 2}, "student-marketplace/dev/products/product-12", "photo.jpg"));
        assertTrue(service.destroyAsset("p12", "image"));

        Map<String, Object> sigMap = service.generateUploadSignature("student-marketplace/dev/tutorials/videos", "tut_abc");
        assertEquals("sig", sigMap.get("signature"));
        assertEquals("tut_abc", sigMap.get("public_id"));
        assertEquals("abc", sigMap.get("upload_session_id"));
    }
}
