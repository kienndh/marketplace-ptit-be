package com.ptiters.marketplace.report;

import tools.jackson.databind.JsonNode;
import com.ptiters.marketplace.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Payload chế tài (khớp {@code AdminEnforcementRequest} của Python, {@code extra="forbid"}):
 * {@code action} ∈ USER_BAN, USER_UNBAN, PRODUCT_HIDE, PRODUCT_RESTORE, CAPABILITY_RESTRICT, CAPABILITY_RESTORE.
 */
public record AdminEnforcementRequest(String action, List<String> capabilities, Integer durationDays) {

    private static final Set<String> FIELDS = Set.of("action", "capabilities", "duration_days");
    private static final Set<String> ACTIONS = Set.of(
            "USER_BAN", "USER_UNBAN", "PRODUCT_HIDE", "PRODUCT_RESTORE", "CAPABILITY_RESTRICT", "CAPABILITY_RESTORE");
    private static final Set<String> CAPS = Set.of("BUY", "SELL");

    public static AdminEnforcementRequest parse(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw invalid("Malformed or unreadable request body.");
        }
        for (String n : body.propertyNames()) {
            if (!FIELDS.contains(n)) {
                throw invalid("Trường '" + n + "' không được phép.");
            }
        }
        if (!body.has("action") || !body.get("action").isTextual()) {
            throw invalid("action là bắt buộc.");
        }
        String action = body.get("action").asText();
        if (!ACTIONS.contains(action)) {
            throw invalid("Hành động chế tài '" + action + "' không hợp lệ.");
        }

        List<String> caps = null;
        boolean capsPresent = body.has("capabilities") && !body.get("capabilities").isNull();
        if (capsPresent) {
            JsonNode arr = body.get("capabilities");
            if (!arr.isArray()) {
                throw invalid("capabilities phải là danh sách.");
            }
            caps = new ArrayList<>();
            for (JsonNode c : arr) {
                caps.add(c.asText());
            }
        }
        boolean durationKeyPresent = body.has("duration_days");
        Integer duration = null;
        if (durationKeyPresent && !body.get("duration_days").isNull()) {
            JsonNode d = body.get("duration_days");
            if (!d.isIntegralNumber() || !d.canConvertToInt()) {
                throw invalid("duration_days phải là số nguyên.");
            }
            duration = d.intValue();
        }

        switch (action) {
            case "CAPABILITY_RESTRICT" -> {
                validateCaps(caps, "CAPABILITY_RESTRICT");
                if (duration == null) {
                    throw invalid("duration_days là bắt buộc đối với CAPABILITY_RESTRICT (chọn 1, 3, 7 hoặc 14 ngày).");
                }
                if (!Set.of(1, 3, 7, 14).contains(duration)) {
                    throw invalid("duration_days chỉ chấp nhận 1, 3, 7 hoặc 14 ngày.");
                }
            }
            case "CAPABILITY_RESTORE" -> {
                validateCaps(caps, "CAPABILITY_RESTORE");
                if (durationKeyPresent) {
                    throw invalid("duration_days không được cung cấp đối với CAPABILITY_RESTORE.");
                }
            }
            default -> {
                if (capsPresent) {
                    throw invalid("capabilities không áp dụng cho hành động '" + action + "'.");
                }
                if (durationKeyPresent) {
                    throw invalid("duration_days không áp dụng cho hành động '" + action + "'.");
                }
            }
        }
        return new AdminEnforcementRequest(action, caps, duration);
    }

    private static void validateCaps(List<String> caps, String action) {
        if (caps == null || caps.isEmpty()) {
            throw invalid("capabilities là bắt buộc đối với " + action + ".");
        }
        if (!CAPS.containsAll(caps)) {
            throw invalid("capabilities phải là danh sách không rỗng chứa 'BUY' và/hoặc 'SELL'.");
        }
        if (caps.size() != Set.copyOf(caps).size()) {
            throw invalid("capabilities không được chứa phần tử trùng lặp.");
        }
    }

    private static ApiException invalid(String msg) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, msg);
    }
}
