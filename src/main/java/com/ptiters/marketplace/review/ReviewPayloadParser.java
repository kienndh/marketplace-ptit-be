package com.ptiters.marketplace.review;

import tools.jackson.databind.JsonNode;
import com.ptiters.marketplace.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Set;

/**
 * Kiểm tra payload review giống Pydantic ({@code extra="forbid"}, rating 1..5, comment tối đa 1000 ký tự,
 * comment toàn khoảng trắng → null). Dùng {@link JsonNode} để phân biệt "không gửi" và "gửi null" (PATCH).
 */
final class ReviewPayloadParser {

    private static final Set<String> ALLOWED = Set.of("rating", "comment");
    static final int MAX_COMMENT = 1000;

    record Parsed(boolean ratingPresent, Integer rating, boolean commentPresent, String comment) {}

    private ReviewPayloadParser() {}

    static Parsed parse(JsonNode body, boolean ratingRequired) {
        if (body == null || !body.isObject()) {
            throw unprocessable("Malformed or unreadable request body.");
        }
        for (String n : body.propertyNames()) {
            if (!ALLOWED.contains(n)) {
                throw unprocessable("Trường '" + n + "' không được phép.");
            }
        }
        boolean ratingPresent = body.has("rating");
        Integer rating = null;
        if (ratingPresent) {
            JsonNode r = body.get("rating");
            if (r.isNull()) {
                throw unprocessable("Số sao đánh giá không được để trống (null).");
            }
            rating = toInt(r);
            if (rating == null || rating < 1 || rating > 5) {
                throw unprocessable("Đánh giá phải từ 1 đến 5 sao.");
            }
        } else if (ratingRequired) {
            throw unprocessable("Số sao đánh giá là bắt buộc.");
        }
        boolean commentPresent = body.has("comment");
        String comment = null;
        if (commentPresent && !body.get("comment").isNull()) {
            JsonNode c = body.get("comment");
            if (!c.isTextual()) {
                throw unprocessable("Nội dung nhận xét phải là chuỗi.");
            }
            if (c.asText().length() > MAX_COMMENT) {
                throw unprocessable("Nội dung nhận xét tối đa 1000 ký tự.");
            }
            String trimmed = c.asText().strip();
            comment = trimmed.isEmpty() ? null : trimmed;
        }
        if (!ratingRequired && !ratingPresent && !commentPresent) {
            throw unprocessable("Phải cung cấp ít nhất một trường để cập nhật (rating hoặc comment).");
        }
        return new Parsed(ratingPresent, rating, commentPresent, comment);
    }

    private static Integer toInt(JsonNode n) {
        if (n.isIntegralNumber() && n.canConvertToInt()) {
            return n.intValue();
        }
        if (n.isFloatingPointNumber() && n.doubleValue() == Math.rint(n.doubleValue())) {
            return (int) n.doubleValue();
        }
        if (n.isTextual()) {
            try {
                return Integer.parseInt(n.asText().strip());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static ApiException unprocessable(String msg) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, msg);
    }
}
