package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record SupporterLeaderboardItem(
        int rank,
        SupporterPublicIdentity supporter,
        @JsonProperty("total_amount_vnd") long totalAmountVnd,
        @JsonProperty("contribution_count") long contributionCount,
        @JsonProperty("last_supported_at") OffsetDateTime lastSupportedAt
) {}
