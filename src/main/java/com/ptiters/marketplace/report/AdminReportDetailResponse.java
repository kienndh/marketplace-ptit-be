package com.ptiters.marketplace.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ptiters.marketplace.report.ReportSummaries.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Khớp {@code AdminReportDetailResponse} của Python (= AdminReportResponse + phần chi tiết). */
public record AdminReportDetailResponse(
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
        @JsonProperty("evidence_count") long evidenceCount,
        List<ReportEvidenceMetadata> evidence,
        @JsonProperty("enforcement_actions") List<ModerationActionSummary> enforcementActions,
        @JsonProperty("available_moderation_actions") List<String> availableModerationActions,
        @JsonProperty("available_enforcement_actions") List<String> availableEnforcementActions,
        @JsonProperty("reported_subject") ReportedSubjectSummary reportedSubject
) {
    public static AdminReportDetailResponse of(AdminReportResponse b, List<ReportEvidenceMetadata> evidence,
                                               List<ModerationActionSummary> actions, List<String> moderationOptions,
                                               List<String> enforcementOptions, ReportedSubjectSummary subject) {
        return new AdminReportDetailResponse(b.reportId(), b.reporterId(), b.reporter(), b.targetType(), b.targetId(),
                b.targetAvailable(), b.targetSummary(), b.reason(), b.description(), b.status(), b.workflowState(),
                b.conclusion(), b.handledBy(), b.handler(), b.handledAt(), b.createdAt(), b.evidenceCount(),
                evidence, actions, moderationOptions, enforcementOptions, subject);
    }
}
