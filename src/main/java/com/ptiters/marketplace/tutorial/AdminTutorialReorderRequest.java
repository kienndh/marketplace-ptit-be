package com.ptiters.marketplace.tutorial;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AdminTutorialReorderRequest(
    @NotBlank @JsonProperty("feature_key") String featureKey,
    @NotEmpty @JsonProperty("tutorial_ids") List<Long> tutorialIds
) {}
