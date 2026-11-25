package com.ptiters.marketplace.report;

import tools.jackson.databind.JsonNode;
import com.ptiters.marketplace.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Payload tạo báo cáo (khớp {@code ReportCreateRequest} của Python, {@code extra="forbid"}).
 * USER: chỉ {@code target_public_id}; PRODUCT/ORDER: chỉ {@code target_id}.
 */
public record ReportCreateRequest(
        ReportTargetType targetType,
        Long targetId,
        UUID targetPublicId,
        ReportReason reason,
        String description
) {

    private static final Set<String> ALLOWED = Set.of("target_type", "target_id", "target_public_id", "reason", "description");

    /** Ma trận lý do theo loại đối tượng (REPORT_REASON_MATRIX). */
    static final Map<ReportTargetType, Set<ReportReason>> REASON_MATRIX = Map.of(
            ReportTargetType.USER, EnumSet.of(ReportReason.SCAM, ReportReason.SPAM, ReportReason.INAPPROPRIATE, ReportReason.OTHER),
            ReportTargetType.PRODUCT, EnumSet.of(ReportReason.SCAM, ReportReason.FAKE_ITEM, ReportReason.SPAM,
                    ReportReason.INAPPROPRIATE, ReportReason.OTHER),
            ReportTargetType.ORDER, EnumSet.of(ReportReason.SCAM, ReportReason.FAKE_ITEM, ReportReason.SPAM,
                    ReportReason.INAPPROPRIATE, ReportReason.NO_SHOW, ReportReason.OTHER));

    public static ReportCreateRequest parse(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw invalid("Malformed or unreadable request body.");
        }
        for (String n : body.propertyNames()) {
            if (!ALLOWED.contains(n)) {
                throw invalid("Trường '" + n + "' không được phép.");
            }
        }
        ReportTargetType type = enumOf(body, "target_type", ReportTargetType.class);
        ReportReason reason = enumOf(body, "reason", ReportReason.class);
        if (type == null) {
            throw invalid("target_type là bắt buộc.");
        }
        if (reason == null) {
            throw invalid("reason là bắt buộc.");
        }

        Long targetId = null;
        if (hasValue(body, "target_id")) {
            JsonNode n = body.get("target_id");
            if (!n.isIntegralNumber() || n.longValue() <= 0) {
                throw invalid("target_id phải là số nguyên dương.");
            }
            targetId = n.longValue();
        }
        UUID publicId = null;
        if (hasValue(body, "target_public_id")) {
            try {
                publicId = UUID.fromString(body.get("target_public_id").asText());
            } catch (IllegalArgumentException e) {
                throw invalid("target_public_id không phải UUID hợp lệ.");
            }
        }

        String description = null;
        if (hasValue(body, "description")) {
            JsonNode d = body.get("description");
            if (!d.isTextual()) {
                throw invalid("description phải là chuỗi.");
            }
            String trimmed = d.asText().strip();
            if (trimmed.length() > 1000) {
                throw invalid("Mô tả tối đa 1000 ký tự.");
            }
            description = trimmed.isEmpty() ? null : trimmed;
        }

        if (type == ReportTargetType.USER) {
            if (targetId != null) {
                throw invalid("Báo cáo người dùng chỉ chấp nhận target_public_id (UUID), không chấp nhận target_id số nguyên.");
            }
            if (publicId == null) {
                throw invalid("target_public_id là bắt buộc khi báo cáo người dùng.");
            }
        } else {
            if (publicId != null) {
                throw invalid("target_public_id chỉ áp dụng cho báo cáo người dùng.");
            }
            if (targetId == null) {
                throw invalid("target_id là bắt buộc đối với báo cáo sản phẩm hoặc đơn hàng.");
            }
        }
        if (!REASON_MATRIX.get(type).contains(reason)) {
            throw invalid("Lý do báo cáo '" + reason.name() + "' không phù hợp với loại đối tượng '" + type.name() + "'.");
        }
        if (reason == ReportReason.OTHER && description == null) {
            throw invalid("Mô tả chi tiết là bắt buộc khi chọn lý do 'Khác'.");
        }
        return new ReportCreateRequest(type, targetId, publicId, reason, description);
    }

    private static boolean hasValue(JsonNode body, String field) {
        return body.has(field) && !body.get(field).isNull();
    }

    private static <E extends Enum<E>> E enumOf(JsonNode body, String field, Class<E> type) {
        if (!hasValue(body, field)) {
            return null;
        }
        try {
            return Enum.valueOf(type, body.get(field).asText());
        } catch (IllegalArgumentException e) {
            throw invalid("Giá trị '" + body.get(field).asText() + "' không hợp lệ cho " + field + ".");
        }
    }

    private static ApiException invalid(String msg) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, msg);
    }
}
