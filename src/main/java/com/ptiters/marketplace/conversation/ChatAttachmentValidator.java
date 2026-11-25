package com.ptiters.marketplace.conversation;

import com.ptiters.marketplace.exception.ApiException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Kiểm tra magic bytes của tệp đính kèm chat — port 1-1 {@code _validate_file_content_signature}
 * ({@code conversations/service.py}). Chat chấp nhận ảnh/video/âm thanh/PDF/Office/TXT (khác
 * {@code realtime.MediaValidator} vốn dành cho bằng chứng ảnh/video, trả 422), nên cần bộ kiểm tra riêng
 * với mã lỗi 400 và thông điệp giống Python.
 */
@Component
public class ChatAttachmentValidator {

    public record Detected(MessageAttachmentKind kind, String mime) {
    }

    public Detected validate(byte[] fullBytes, String filename) {
        byte[] header = Arrays.copyOf(fullBytes, Math.min(32, fullBytes.length));
        if (header.length < 4) {
            throw ApiException.badRequest("Tệp tin rỗng hoặc bị lỗi.");
        }
        String lowerName = filename == null ? "" : filename.toLowerCase(Locale.ROOT);

        if (starts(header, 'M', 'Z') || starts(header, 0x7f, 'E', 'L', 'F')) {
            throw ApiException.badRequest("Executable files are not permitted.");
        }
        if (starts(header, 0xff, 0xd8, 0xff)) {
            return new Detected(MessageAttachmentKind.IMAGE, "image/jpeg");
        }
        if (starts(header, 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n')) {
            return new Detected(MessageAttachmentKind.IMAGE, "image/png");
        }
        boolean riff = starts(header, 'R', 'I', 'F', 'F') && header.length >= 12;
        if (riff && ascii(header, 8, 12).equals("WEBP")) {
            return new Detected(MessageAttachmentKind.IMAGE, "image/webp");
        }
        if (ascii(header, 0, Math.min(6, header.length)).equals("GIF87a")
                || ascii(header, 0, Math.min(6, header.length)).equals("GIF89a")) {
            return new Detected(MessageAttachmentKind.IMAGE, "image/gif");
        }
        if (starts(header, '%', 'P', 'D', 'F', '-')) {
            return new Detected(MessageAttachmentKind.PDF, "application/pdf");
        }
        if (starts(header, 'I', 'D', '3') || ((header[0] & 0xff) == 0xff && (header[1] & 0xe0) == 0xe0)) {
            return new Detected(MessageAttachmentKind.AUDIO, "audio/mpeg");
        }
        if (starts(header, 'O', 'g', 'g', 'S')) {
            return new Detected(MessageAttachmentKind.AUDIO, "audio/ogg");
        }
        if (riff && ascii(header, 8, 12).equals("WAVE")) {
            return new Detected(MessageAttachmentKind.AUDIO, "audio/wav");
        }
        if (starts(header, 0x1a, 0x45, 0xdf, 0xa3)) {
            if (lowerName.endsWith(".weba") || lowerName.endsWith(".ogg") || lowerName.endsWith(".opus")
                    || lowerName.contains("audio") || lowerName.contains("voice")) {
                return new Detected(MessageAttachmentKind.AUDIO, "audio/webm");
            }
            return new Detected(MessageAttachmentKind.VIDEO, "video/webm");
        }
        if (header.length >= 12 && ascii(header, 0, Math.min(16, header.length)).contains("ftyp")) {
            if (ascii(header, 0, Math.min(16, header.length)).contains("ftypM4A")
                    || lowerName.endsWith(".m4a") || lowerName.endsWith(".aac")) {
                return new Detected(MessageAttachmentKind.AUDIO, "audio/mp4");
            }
            return new Detected(MessageAttachmentKind.VIDEO, "video/mp4");
        }
        if (starts(header, 'P', 'K', 0x03, 0x04)) {
            if (isValidOfficeOpenXml(fullBytes)) {
                if (lowerName.endsWith(".xlsx")) {
                    return new Detected(MessageAttachmentKind.DOCUMENT,
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                }
                if (lowerName.endsWith(".pptx")) {
                    return new Detected(MessageAttachmentKind.DOCUMENT,
                            "application/vnd.openxmlformats-officedocument.presentationml.presentation");
                }
                return new Detected(MessageAttachmentKind.DOCUMENT,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            }
            throw ApiException.badRequest("Tệp nén zip hoặc tệp Office giả mạo không được hỗ trợ.");
        }
        if (lowerName.endsWith(".txt") && !contains(header, (byte) 0) && isUtf8Prefix(fullBytes)) {
            return new Detected(MessageAttachmentKind.DOCUMENT, "text/plain");
        }
        throw ApiException.badRequest(
                "Định dạng tệp không được hỗ trợ. Chỉ nhận ảnh, video, ghi âm, PDF, TXT, DOCX, XLSX, PPTX.");
    }

    /** Python: {@code _is_valid_office_open_xml} — ZIP phải chứa [Content_Types].xml. */
    private boolean isValidOfficeOpenXml(byte[] bytes) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("[Content_Types].xml".equals(entry.getName())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** Giải mã UTF-8 nghiêm ngặt 1024 byte đầu (chấp nhận ký tự đa byte bị cắt ở biên). */
    private boolean isUtf8Prefix(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, 0, Math.min(1024, bytes.length)));
            return true;
        } catch (CharacterCodingException e) {
            // Có thể do cắt giữa ký tự đa byte: thử lùi tối đa 3 byte.
            for (int cut = 1; cut <= 3 && Math.min(1024, bytes.length) - cut > 0; cut++) {
                try {
                    StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes, 0, Math.min(1024, bytes.length) - cut));
                    return bytes.length > 1024;
                } catch (CharacterCodingException ignored) {
                    // tiếp tục thử
                }
            }
            return false;
        }
    }

    private static boolean starts(byte[] data, int... prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xff) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean contains(byte[] data, byte b) {
        for (byte x : data) {
            if (x == b) {
                return true;
            }
        }
        return false;
    }

    private static String ascii(byte[] data, int from, int to) {
        StringBuilder sb = new StringBuilder(Math.max(0, to - from));
        for (int i = from; i < to && i < data.length; i++) {
            sb.append((char) (data[i] & 0xff));
        }
        return sb.toString();
    }
}
