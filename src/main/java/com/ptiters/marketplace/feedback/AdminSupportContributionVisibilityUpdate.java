package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record AdminSupportContributionVisibilityUpdate(
    @NotNull @JsonProperty("is_public") Boolean isPublic
) {}
