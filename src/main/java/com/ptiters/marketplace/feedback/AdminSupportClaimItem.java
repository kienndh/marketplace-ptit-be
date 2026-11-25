package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record AdminSupportClaimItem(
        @JsonProperty("claim_id") Long claimId,
        SupporterResolveResponse supporter,
        @JsonProperty("claimed_amount_vnd") Long claimedAmountVnd,
        @JsonProperty("claimed_transferred_at") OffsetDateTime claimedTransferredAt,
        @JsonProperty("sender_account_name") String senderAccountName,
        @JsonProperty("wants_public") Boolean wantsPublic,
        String status,
        @JsonProperty("reviewed_by") AdminUserSafeIdentity reviewedBy,
        @JsonProperty("reviewed_at") OffsetDateTime reviewedAt,
        @JsonProperty("contribution_id") Long contributionId,
        @JsonProperty("rejection_reason") String rejectionReason,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {}
