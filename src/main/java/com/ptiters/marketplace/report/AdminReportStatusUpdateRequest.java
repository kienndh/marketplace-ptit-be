package com.ptiters.marketplace.report;

import tools.jackson.databind.JsonNode;
import com.ptiters.marketplace.exception.ApiException;
import org.springframework.http.HttpStatus;

/** Khớp {@code AdminReportStatusUpdateRequest}: chỉ có {@code status} ({@code extra="forbid"}). */
public record AdminReportStatusUpdateRequest(ReportStatus status) {

    public static AdminReportStatusUpdateRequest parse(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw invalid("Malformed or unreadable request body.");
        }
        for (String n : body.propertyNames()) {
            if (!"status".equals(n)) {
                throw invalid("Trường '" + n + "' không được phép.");
            }
        }
        if (!body.has("status") || !body.get("status").isTextual()) {
            throw invalid("status là bắt buộc.");
        }
        try {
            return new AdminReportStatusUpdateRequest(ReportStatus.valueOf(body.get("status").asText()));
        } catch (IllegalArgumentException e) {
            throw invalid("Giá trị status '" + body.get("status").asText() + "' không hợp lệ.");
        }
    }

    private static ApiException invalid(String msg) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, msg);
    }
}
