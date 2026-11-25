package com.ptiters.marketplace.tutorial;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminTutorialCreateDirectRequest(
    @NotBlank @JsonProperty("feature_key") String featureKey,
    @NotBlank @Size(max = 120) String title,
    @Size(max = 500) String description,
    @JsonProperty("action_key") String actionKey,
    @JsonProperty("is_published") boolean isPublished,
    @NotBlank @JsonProperty("public_id") String publicId,
    @NotBlank @JsonProperty("upload_session_id") String uploadSessionId,
    @JsonProperty("secure_url") String secureUrl,
    @JsonProperty("video_size_bytes") Long videoSizeBytes,
    @JsonProperty("duration_ms") Long durationMs
) {}
