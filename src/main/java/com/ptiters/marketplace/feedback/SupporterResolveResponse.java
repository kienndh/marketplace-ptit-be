package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SupporterResolveResponse(
        @JsonProperty("user_id") Long userId,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("profile_path") String profilePath
) {}
