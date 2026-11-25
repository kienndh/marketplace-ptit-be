package com.ptiters.marketplace.report;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/** Các DTO con dùng chung cho response báo cáo (khớp {@code reports/schemas.py}). */
public final class ReportSummaries {

    private ReportSummaries() {}

    public record ReporterSummary(
            @JsonProperty("user_id") Long userId,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("avatar_url") String avatarUrl
    ) {}

    public record AdminHandlerSummary(
            @JsonProperty("user_id") Long userId,
            @JsonProperty("full_name") String fullName
    ) {}

    public record ReportEvidenceMetadata(
            @JsonProperty("attachment_id") Long attachmentId,
            @JsonProperty("original_name") String originalName,
            @JsonProperty("mime_type") String mimeType,
            @JsonProperty("size_bytes") Long sizeBytes,
            @JsonProperty("created_at") LocalDateTime createdAt,
            @JsonProperty("media_type") String mediaType
    ) {}

    public record ModerationActionSummary(
            @JsonProperty("moderation_action_id") Long moderationActionId,
            @JsonProperty("action_type") String actionType,
            @JsonProperty("previous_status") String previousStatus,
            @JsonProperty("new_status") String newStatus,
            AdminHandlerSummary admin,
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {}

    public record AdminCapabilityRestrictionResponse(
            String capability,
            @JsonProperty("expires_at") LocalDateTime expiresAt,
            @JsonProperty("restorable_from_current_report") boolean restorableFromCurrentReport
    ) {}

    public record ReportedSubjectSummary(
            @JsonProperty("user_id") Long userId,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("avatar_url") String avatarUrl,
            @JsonProperty("role_in_order") String roleInOrder,
            String status,
            @JsonProperty("can_buy") boolean canBuy,
            @JsonProperty("can_sell") boolean canSell,
            @JsonProperty("active_restrictions") List<AdminCapabilityRestrictionResponse> activeRestrictions,
            @JsonProperty("prior_report_count") long priorReportCount
    ) {}

    public record SubjectReportHistoryEnforcementAction(
            @JsonProperty("action_type") String actionType,
            @JsonProperty("previous_status") String previousStatus,
            @JsonProperty("new_status") String newStatus
    ) {}

    public record SubjectReportHistoryItem(
            @JsonProperty("report_id") Long reportId,
            @JsonProperty("target_type") String targetType,
            String reason,
            String status,
            @JsonProperty("created_at") LocalDateTime createdAt,
            @JsonProperty("handled_at") LocalDateTime handledAt,
            @JsonProperty("evidence_count") long evidenceCount,
            @JsonProperty("enforcement_actions") List<SubjectReportHistoryEnforcementAction> enforcementActions
    ) {}
}
