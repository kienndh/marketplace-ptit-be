package com.ptiters.marketplace.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReportCreateRequest(
    @NotNull @JsonProperty("target_type") ReportTargetType targetType,
    @NotNull @JsonProperty("target_id") Long targetId,
    @NotNull ReportReason reason,
    @NotBlank String description
) {}
