package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record AdminSupportContributionVisibilityUpdate(
        @NotNull(message = "is_public không được để trống.")
        @JsonProperty("is_public") Boolean isPublic
) {}
