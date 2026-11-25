package com.ptiters.marketplace.communitymoment;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record PublicCommunityMomentItem(
    @JsonProperty("moment_id") Long momentId,
    @JsonProperty("image_url") String imageUrl,
    String note,
    @JsonProperty("approved_at") LocalDateTime approvedAt
) {}
