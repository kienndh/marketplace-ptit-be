package com.ptiters.marketplace.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record UserSearchResponse(
    @JsonProperty("user_id") Long userId,
    @JsonProperty("public_id") UUID publicId,
    @JsonProperty("full_name") String fullName,
    @JsonProperty("avatar_url") String avatarUrl
) {}
