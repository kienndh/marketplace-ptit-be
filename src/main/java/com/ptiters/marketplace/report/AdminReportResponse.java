package com.ptiters.marketplace.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ptiters.marketplace.report.ReportSummaries.AdminHandlerSummary;
import com.ptiters.marketplace.report.ReportSummaries.ReporterSummary;

import java.time.LocalDateTime;
import java.util.Map;

/** Khớp {@code AdminReportResponse} của Python (mỗi phần tử hàng đợi báo cáo). */
public record AdminReportResponse(
        @JsonProperty("report_id") Long reportId,
        @JsonProperty("reporter_id") Long reporterId,
        ReporterSummary reporter,
        @JsonProperty("target_type") String targetType,
        @JsonProperty("target_id") Long targetId,
        @JsonProperty("target_available") boolean targetAvailable,
        @JsonProperty("target_summary") Map<String, Object> targetSummary,
        String reason,
        String description,
        String status,
        @JsonProperty("workflow_state") String workflowState,
        String conclusion,
        @JsonProperty("handled_by") Long handledBy,
        AdminHandlerSummary handler,
        @JsonProperty("handled_at") LocalDateTime handledAt,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("evidence_count") long evidenceCount
) {}
