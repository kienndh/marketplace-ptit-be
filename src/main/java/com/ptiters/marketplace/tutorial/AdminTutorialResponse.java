package com.ptiters.marketplace.tutorial;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record AdminTutorialResponse(
    @JsonProperty("tutorial_id") Long tutorialId,
    @JsonProperty("feature_key") String featureKey,
    String title,
    String description,
    @JsonProperty("video_url") String videoUrl,
    @JsonProperty("thumbnail_url") String thumbnailUrl,
    @JsonProperty("action_key") String actionKey,
    @JsonProperty("sort_order") Integer sortOrder,
    @JsonProperty("is_published") Boolean isPublished,
    @JsonProperty("video_size_bytes") Long videoSizeBytes,
    @JsonProperty("duration_ms") Long durationMs,
    @JsonProperty("created_by") Long createdBy,
    @JsonProperty("created_at") LocalDateTime createdAt,
    @JsonProperty("updated_at") LocalDateTime updatedAt
) {}
