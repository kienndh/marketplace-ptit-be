package com.ptiters.marketplace.tutorial;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record AdminTutorialConfirmDirectVideo(
    @NotBlank @JsonProperty("public_id") String publicId,
    @NotBlank @JsonProperty("upload_session_id") String uploadSessionId,
    @JsonProperty("secure_url") String secureUrl,
    @JsonProperty("video_size_bytes") Long videoSizeBytes,
    @JsonProperty("duration_ms") Long durationMs
) {}
