package com.ptiters.marketplace.communitymoment;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdminCommunityMomentUploader(
    @JsonProperty("user_id") Long userId,
    @JsonProperty("full_name") String fullName,
    @JsonProperty("avatar_url") String avatarUrl
) {}
