package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record SupportClaimUserItem(
    Long id,
    @JsonProperty("contribution_id") Long contributionId,
    String status,
    @JsonProperty("proof_url") String proofUrl,
    @JsonProperty("created_at") LocalDateTime createdAt
) {}
