package com.ptiters.marketplace.order;

import com.ptiters.marketplace.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;

/**
 * Tiện ích thời gian: DB lưu UTC naive ({@code LocalDateTime}); client có thể gửi ISO có/không offset
 * (Python: {@code normalize_to_utc_naive}).
 */
public final class DateTimes {

    private DateTimes() {
    }

    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /** Parse ISO-8601 (có hoặc không offset/Z) → UTC naive. Null/blank → null. Sai định dạng → 422. */
    public static LocalDateTime parseToUtcNaive(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            TemporalAccessor parsed = DateTimeFormatter.ISO_DATE_TIME.parseBest(
                    raw.trim(), OffsetDateTime::from, LocalDateTime::from);
            if (parsed instanceof OffsetDateTime odt) {
                return odt.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
            }
            return (LocalDateTime) parsed;
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Định dạng thời gian không hợp lệ: " + fieldName);
        }
    }
}
