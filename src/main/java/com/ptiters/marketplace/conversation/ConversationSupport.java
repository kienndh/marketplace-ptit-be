package com.ptiters.marketplace.conversation;

import com.ptiters.marketplace.exception.ApiException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Tiện ích thuần (không trạng thái) dùng chung trong package conversation. */
final class ConversationSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ConversationSupport() {
    }

    /** UTC "naive" cắt về micro-giây (PostgreSQL timestamp chỉ lưu tới µs → cursor khứ hồi chính xác). */
    static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    /** Python: {@code aware.isoformat().replace("+00:00", "Z")}. */
    static String isoZ(LocalDateTime dt) {
        return dt == null ? null : dt.atOffset(ZoneOffset.UTC).toInstant().toString();
    }

    static ApiException unprocessable(String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }

    static ApiException unavailable(String detail) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, detail);
    }

    // ----------------------------------------------------------------------------------------- cursor

    /** Python: encode_cursor — JSON → URL-safe Base64. */
    static String encodeCursor(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(k1, v1);
        data.put(k2, v2);
        return Base64.getUrlEncoder().encodeToString(JSON.writeValueAsString(data).getBytes(StandardCharsets.UTF_8));
    }

    /** Python: decode_cursor — mọi lỗi định dạng ném {@link InvalidCursorException}. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> decodeCursor(String cursor) {
        try {
            byte[] raw = Base64.getUrlDecoder().decode(cursor.getBytes(StandardCharsets.UTF_8));
            Object parsed = JSON.readValue(new String(raw, StandardCharsets.UTF_8), Map.class);
            if (!(parsed instanceof Map<?, ?> map)) {
                throw new InvalidCursorException();
            }
            return (Map<String, Object>) map;
        } catch (RuntimeException e) {
            throw new InvalidCursorException();
        }
    }

    /** Đọc timestamp trong cursor (Python: datetime.fromisoformat). */
    static LocalDateTime cursorTime(Map<String, Object> data, String key) {
        try {
            return LocalDateTime.parse(String.valueOf(data.get(key)));
        } catch (RuntimeException e) {
            throw new InvalidCursorException();
        }
    }

    static long cursorLong(Map<String, Object> data, String key) {
        try {
            return Long.parseLong(String.valueOf(data.get(key)));
        } catch (RuntimeException e) {
            throw new InvalidCursorException();
        }
    }

    /** Python: ValueError("Invalid cursor format"). */
    static final class InvalidCursorException extends RuntimeException {
        InvalidCursorException() {
            super("Invalid cursor format");
        }
    }

    // ---------------------------------------------------------------------------------- JSON dùng chung

    static JsonMapper json() {
        return JSON;
    }
}
