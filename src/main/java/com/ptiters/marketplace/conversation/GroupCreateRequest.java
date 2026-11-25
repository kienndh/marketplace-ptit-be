package com.ptiters.marketplace.conversation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GroupCreateRequest(
        String title,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("member_ids") List<Long> memberIds
) {}
