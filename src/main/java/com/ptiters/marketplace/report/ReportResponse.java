package com.ptiters.marketplace.report;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/** Khớp {@code ReportResponse} (API_CONTRACT 20.1): không lộ reporter_id/handled_by/handled_at. */
public record ReportResponse(
        @JsonProperty("report_id") Long reportId,
        @JsonProperty("target_type") String targetType,
        @JsonProperty("target_id") Long targetId,
        String reason,
        String description,
        String status,
        @JsonProperty("created_at") LocalDateTime createdAt
) {}
