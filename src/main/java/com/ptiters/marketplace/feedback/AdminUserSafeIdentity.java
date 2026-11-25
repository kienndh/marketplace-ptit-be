package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdminUserSafeIdentity(
        @JsonProperty("user_id") Long userId,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("avatar_url") String avatarUrl
) {}
