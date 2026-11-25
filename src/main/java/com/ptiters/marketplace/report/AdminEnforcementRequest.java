package com.ptiters.marketplace.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record AdminEnforcementRequest(
    @NotNull @JsonProperty("action_type") ModerationActionType actionType,
    String reason,
    @JsonProperty("duration_days") Integer durationDays
) {}
