package com.ptiters.marketplace.tutorial;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PublicTutorialResponse(
    @JsonProperty("tutorial_id") Long tutorialId,
    @JsonProperty("feature_key") String featureKey,
    String title,
    String description,
    @JsonProperty("video_url") String videoUrl,
    @JsonProperty("thumbnail_url") String thumbnailUrl,
    @JsonProperty("action_key") String actionKey,
    @JsonProperty("sort_order") Integer sortOrder
) {}
