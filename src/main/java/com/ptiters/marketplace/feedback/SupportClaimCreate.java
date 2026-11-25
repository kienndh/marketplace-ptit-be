package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record SupportClaimCreate(
    @NotNull @JsonProperty("contribution_id") Long contributionId,
    @JsonProperty("proof_url") String proofUrl
) {}
