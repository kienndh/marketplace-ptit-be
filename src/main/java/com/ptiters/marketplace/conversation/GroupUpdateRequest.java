package com.ptiters.marketplace.conversation;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GroupUpdateRequest(
        String title,
        @JsonProperty("avatar_url") String avatarUrl
) {}
