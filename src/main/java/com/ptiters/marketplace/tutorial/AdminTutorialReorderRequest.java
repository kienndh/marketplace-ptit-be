package com.ptiters.marketplace.tutorial;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AdminTutorialReorderRequest(
    @NotNull @JsonProperty("feature_key") String featureKey,
    @NotEmpty @JsonProperty("tutorial_ids") List<Long> tutorialIds
) {}
