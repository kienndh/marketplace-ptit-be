package com.ptiters.marketplace.communitymoment;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CommunityMomentActionResponse(
    @JsonProperty("moment_id") Long momentId,
    String status,
    String message
) {}
