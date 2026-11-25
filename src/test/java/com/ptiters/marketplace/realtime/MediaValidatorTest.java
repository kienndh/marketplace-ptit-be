package com.ptiters.marketplace.realtime;

import com.ptiters.marketplace.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Đối chiếu hành vi với {@code app/core/media_validation.py}. */
class MediaValidatorTest {

    private final MediaValidator validator = new MediaValidator();

    private static byte[] jpeg(int size) {
        byte[] b = new byte[size];
        b[0] = (byte) 0xff;
        b[1] = (byte) 0xd8;
        b[2] = (byte) 0xff;
        return b;
    }

    private static byte[] png() {
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 0};
        return sig;
    }

    private static byte[] webp() {
        byte[] b = new byte[16];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, b, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, b, 8, 4);
        return b;
    }

    private static byte[] mp4(String brand, int size) {
        byte[] b = new byte[size];
        System.arraycopy("ftyp".getBytes(StandardCharsets.US_ASCII), 0, b, 4, 4);
        System.arraycopy(brand.getBytes(StandardCharsets.US_ASCII), 0, b, 8, 4);
        return b;
    }

    private ApiException assertInvalid(Runnable r) {
        ApiException e = assertThrows(ApiException.class, r::run);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
        return e;
    }

    @Test
    @DisplayName("Ảnh JPEG / PNG / WebP hợp lệ trả MIME chuẩn theo magic bytes")
    void detectsImageMimeByMagicBytes() {
        assertEquals("image/jpeg", validator.validateImageBytes("a.jpg", jpeg(100)));
        assertEquals("image/png", validator.validateImageBytes("a.png", png()));
        assertEquals("image/webp", validator.validateImageBytes("a.webp", webp()));
    }

    @Test
    @DisplayName("Tệp rỗng/ngắn hơn 4 byte bị từ chối")
    void rejectsTinyFiles() {
        ApiException e = assertInvalid(() -> validator.validateImageBytes("x.jpg", new byte[3]));
        assertTrue(e.getMessage().contains("rỗng hoặc bị hỏng"));
    }

    @Test
    @DisplayName("Tệp thực thi (MZ / ELF) bị từ chối dù đặt đuôi .jpg")
    void rejectsExecutables() {
        byte[] exe = Arrays.copyOf("MZ\u0090\u0000".getBytes(StandardCharsets.ISO_8859_1), 64);
        byte[] elf = {0x7f, 'E', 'L', 'F', 1, 1, 1, 0};
        assertTrue(assertInvalid(() -> validator.validateImageBytes("evil.jpg", exe)).getMessage().contains("thực thi"));
        assertTrue(assertInvalid(() -> validator.validateImageBytes("evil.jpg", elf)).getMessage().contains("thực thi"));
        assertTrue(assertInvalid(() -> validator.validateMediaEvidenceBytes("evil.mp4", elf)).getMessage().contains("thực thi"));
    }

    @Test
    @DisplayName("Định dạng ảnh không hỗ trợ (GIF) bị từ chối")
    void rejectsUnsupportedImageFormat() {
        byte[] gif = "GIF89a....".getBytes(StandardCharsets.US_ASCII);
        assertTrue(assertInvalid(() -> validator.validateImageBytes("a.gif", gif)).getMessage().contains("không được hỗ trợ"));
    }

    @Test
    @DisplayName("Ảnh vượt 10MB bị từ chối, đúng 10MB được chấp nhận")
    void enforcesImageSizeLimit() {
        int max = (int) MediaValidator.MAX_IMAGE_SIZE_BYTES;
        assertEquals("image/jpeg", validator.validateImageBytes("ok.jpg", jpeg(max)));
        ApiException e = assertInvalid(() -> validator.validateImageBytes("big.jpg", jpeg(max + 1)));
        assertTrue(e.getMessage().contains("10MB"));
    }

    @Test
    @DisplayName("Bằng chứng: ảnh trả IMAGE, MP4/MOV/WebM trả VIDEO với MIME chuẩn")
    void evidenceDetectsImageAndVideo() {
        assertEquals(new MediaValidator.MediaEvidence("image/png", "IMAGE"),
                validator.validateMediaEvidenceBytes("a.png", png()));
        assertEquals(new MediaValidator.MediaEvidence("video/mp4", "VIDEO"),
                validator.validateMediaEvidenceBytes("a.mp4", mp4("isom", 64)));
        assertEquals(new MediaValidator.MediaEvidence("video/quicktime", "VIDEO"),
                validator.validateMediaEvidenceBytes("a.mp4", mp4("qt  ", 64)));
        assertEquals(new MediaValidator.MediaEvidence("video/quicktime", "VIDEO"),
                validator.validateMediaEvidenceBytes("clip.MOV", mp4("isom", 64)));
        byte[] webm = {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3, 0, 0, 0, 0};
        assertEquals(new MediaValidator.MediaEvidence("video/webm", "VIDEO"),
                validator.validateMediaEvidenceBytes("a.webm", webm));
    }

    @Test
    @DisplayName("Video vượt 30MB bị từ chối; định dạng lạ bị từ chối")
    void evidenceEnforcesVideoLimitAndFormat() {
        int max = (int) MediaValidator.MAX_VIDEO_SIZE_BYTES;
        assertEquals("video/mp4", validator.validateMediaEvidenceBytes("a.mp4", mp4("isom", max)).mimeType());
        assertTrue(assertInvalid(() -> validator.validateMediaEvidenceBytes("a.mp4", mp4("isom", max + 1)))
                .getMessage().contains("30MB"));
        byte[] junk = "not-a-media-file".getBytes(StandardCharsets.US_ASCII);
        assertTrue(assertInvalid(() -> validator.validateMediaEvidenceBytes("a.bin", junk))
                .getMessage().contains("MP4, WebM, MOV"));
    }
}
