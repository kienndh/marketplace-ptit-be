package com.ptiters.marketplace.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record ReportResponse(
    Long id,
    @JsonProperty("target_type") String targetType,
    @JsonProperty("target_id") Long targetId,
    String reason,
    String description,
    String status,
    @JsonProperty("created_at") LocalDateTime createdAt
) {}
