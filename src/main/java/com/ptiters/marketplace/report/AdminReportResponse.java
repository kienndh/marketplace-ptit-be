package com.ptiters.marketplace.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record AdminReportResponse(
    Long id,
    @JsonProperty("reporter_id") Long reporterId,
    @JsonProperty("target_type") String targetType,
    @JsonProperty("target_id") Long targetId,
    String reason,
    String description,
    String status,
    @JsonProperty("workflow_state") String workflowState,
    @JsonProperty("handled_by") Long handledBy,
    @JsonProperty("created_at") LocalDateTime createdAt
) {}
