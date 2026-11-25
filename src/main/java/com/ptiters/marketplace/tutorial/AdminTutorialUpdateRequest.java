package com.ptiters.marketplace.tutorial;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

public record AdminTutorialUpdateRequest(
    @JsonProperty("feature_key") String featureKey,
    @Size(max = 120) String title,
    @Size(max = 500) String description,
    @JsonProperty("action_key") String actionKey,
    @JsonProperty("is_published") Boolean isPublished
) {}
