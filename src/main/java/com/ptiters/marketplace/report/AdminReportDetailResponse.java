package com.ptiters.marketplace.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

public record AdminReportDetailResponse(
    Long id,
    @JsonProperty("reporter_id") Long reporterId,
    @JsonProperty("target_type") String targetType,
    @JsonProperty("target_id") Long targetId,
    String reason,
    String description,
    String status,
    @JsonProperty("workflow_state") String workflowState,
    @JsonProperty("handled_by") Long handledBy,
    @JsonProperty("resolution_notes") String resolutionNotes,
    String conclusion,
    @JsonProperty("created_at") LocalDateTime createdAt,
    @JsonProperty("resolved_at") LocalDateTime resolvedAt,
    List<String> attachments
) {}
