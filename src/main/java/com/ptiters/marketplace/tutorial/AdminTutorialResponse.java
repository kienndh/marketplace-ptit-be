package com.ptiters.marketplace.tutorial;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record AdminTutorialResponse(
    @JsonProperty("tutorial_id") Long tutorialId,
    @JsonProperty("feature_key") String featureKey,
    String title,
    String description,
    @JsonProperty("video_url") String videoUrl,
    @JsonProperty("video_storage_key") String videoStorageKey,
    @JsonProperty("video_mime_type") String videoMimeType,
    @JsonProperty("video_size_bytes") Long videoSizeBytes,
    @JsonProperty("duration_ms") Long durationMs,
    @JsonProperty("thumbnail_url") String thumbnailUrl,
    @JsonProperty("action_key") String actionKey,
    @JsonProperty("sort_order") Integer sortOrder,
    @JsonProperty("is_published") Boolean isPublished,
    @JsonProperty("created_by") Long createdBy,
    @JsonProperty("created_at") OffsetDateTime createdAt,
    @JsonProperty("updated_at") OffsetDateTime updatedAt
) {}
