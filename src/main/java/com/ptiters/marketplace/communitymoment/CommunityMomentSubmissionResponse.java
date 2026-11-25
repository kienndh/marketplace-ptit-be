package com.ptiters.marketplace.communitymoment;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record CommunityMomentSubmissionResponse(
    @JsonProperty("moment_id") Long momentId,
    String status,
    @JsonProperty("created_at") LocalDateTime createdAt
) {}
