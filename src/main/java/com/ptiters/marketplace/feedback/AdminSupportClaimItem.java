package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record AdminSupportClaimItem(
    Long id,
    @JsonProperty("user_id") Long userId,
    @JsonProperty("contribution_id") Long contributionId,
    String status,
    @JsonProperty("proof_url") String proofUrl,
    @JsonProperty("verified_by") Long verifiedBy,
    @JsonProperty("verified_at") LocalDateTime verifiedAt,
    @JsonProperty("created_at") LocalDateTime createdAt
) {}
