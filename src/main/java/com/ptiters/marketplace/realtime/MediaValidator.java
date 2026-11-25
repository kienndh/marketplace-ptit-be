package com.ptiters.marketplace.realtime;

import com.ptiters.marketplace.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Kiểm tra byte của tệp ảnh/video theo magic bytes (không tin Content-Type do client gửi).
 * Hiện thực 1-1 {@code app/core/media_validation.py}; lỗi trả HTTP 422 với thông điệp tiếng Việt giống Python.
 */
@Component
public class MediaValidator {

    public static final long MAX_IMAGE_SIZE_BYTES = 10L * 1024 * 1024;
    public static final long MAX_VIDEO_SIZE_BYTES = 30L * 1024 * 1024;
    public static final double MAX_VIDEO_DURATION_SECONDS = 60.0;
    public static final Set<String> ALLOWED_COMMUNITY_IMAGE_MIMES = Set.of("image/jpeg", "image/png", "image/webp");

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
    private static final byte[] WEBM_SIGNATURE = {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3};
    private static final Set<String> MP4_BOX_TYPES = Set.of("ftyp", "moov", "mdat", "wide", "free", "skip");

    /** Kết quả kiểm tra bằng chứng: MIME chuẩn hoá + loại media ("IMAGE" hoặc "VIDEO"). */
    public record MediaEvidence(String mimeType, String mediaType) {
    }

    /** Python: {@code validate_image_bytes(file_name, file_bytes, max_size)} — trả MIME chuẩn. */
    public String validateImageBytes(String fileName, byte[] bytes) {
        return validateImageBytes(fileName, bytes, MAX_IMAGE_SIZE_BYTES);
    }

    public String validateImageBytes(String fileName, byte[] bytes, long maxSize) {
        rejectEmptyOrExecutable(fileName, bytes);
        String mime = detectImageMime(bytes);
        if (mime == null) {
            throw invalid("Định dạng tệp '" + fileName + "' không được hỗ trợ. Chỉ chấp nhận ảnh (JPEG, PNG, WebP).");
        }
        if (bytes.length > maxSize) {
            throw invalid("Tệp '" + fileName + "' vượt quá dung lượng tối đa cho phép (10MB).");
        }
        return mime;
    }

    /** Python: {@code validate_media_evidence_bytes(file_name, file_bytes)}. */
    public MediaEvidence validateMediaEvidenceBytes(String fileName, byte[] bytes) {
        rejectEmptyOrExecutable(fileName, bytes);
        if (detectImageMime(bytes) != null) {
            return new MediaEvidence(validateImageBytes(fileName, bytes, MAX_IMAGE_SIZE_BYTES), "IMAGE");
        }
        if (startsWith(bytes, WEBM_SIGNATURE)) {
            requireVideoSize(fileName, bytes);
            return new MediaEvidence("video/webm", "VIDEO");
        }
        if (bytes.length >= 8 && MP4_BOX_TYPES.contains(ascii(bytes, 4, 8))) {
            requireVideoSize(fileName, bytes);
            boolean quickTime = false;
            if (bytes.length >= 12 && "ftyp".equals(ascii(bytes, 4, 8))) {
                String brand = ascii(bytes, 8, 12);
                quickTime = "qt  ".equals(brand) || "mqt ".equals(brand);
            }
            if (!quickTime) {
                String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
                quickTime = lower.endsWith(".mov") || lower.endsWith(".qt");
            }
            return new MediaEvidence(quickTime ? "video/quicktime" : "video/mp4", "VIDEO");
        }
        throw invalid("Định dạng tệp '" + fileName
                + "' không được hỗ trợ. Chỉ chấp nhận ảnh (JPEG, PNG, WebP) hoặc video (MP4, WebM, MOV).");
    }

    // ------------------------------------------------------------------ helpers

    private void rejectEmptyOrExecutable(String fileName, byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            throw invalid("Tệp '" + fileName + "' rỗng hoặc bị hỏng.");
        }
        boolean windowsExe = bytes[0] == 'M' && bytes[1] == 'Z';
        boolean elf = bytes[0] == 0x7f && bytes[1] == 'E' && bytes[2] == 'L' && bytes[3] == 'F';
        if (windowsExe || elf) {
            throw invalid("Tệp '" + fileName + "' là tệp thực thi, không được phép tải lên.");
        }
    }

    private void requireVideoSize(String fileName, byte[] bytes) {
        if (bytes.length > MAX_VIDEO_SIZE_BYTES) {
            throw invalid("Tệp '" + fileName + "' vượt quá dung lượng tối đa cho phép đối với video (30MB).");
        }
    }

    private String detectImageMime(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8 && (b[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (startsWith(b, PNG_SIGNATURE)) {
            return "image/png";
        }
        if (b.length >= 12 && "RIFF".equals(ascii(b, 0, 4)) && "WEBP".equals(ascii(b, 8, 12))) {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String ascii(byte[] data, int from, int to) {
        StringBuilder sb = new StringBuilder(to - from);
        for (int i = from; i < to; i++) {
            sb.append((char) (data[i] & 0xff));
        }
        return sb.toString();
    }

    private static ApiException invalid(String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }
}
