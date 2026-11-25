package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record SupportClaimUserItem(
        @JsonProperty("claim_id") Long claimId,
        @JsonProperty("claimed_amount_vnd") Long claimedAmountVnd,
        @JsonProperty("claimed_transferred_at") OffsetDateTime claimedTransferredAt,
        @JsonProperty("sender_account_name") String senderAccountName,
        @JsonProperty("wants_public") Boolean wantsPublic,
        String status,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {
    public static SupportClaimUserItem from(SupportClaim c) {
        return new SupportClaimUserItem(
                c.getClaimId(),
                c.getClaimedAmountVnd(),
                c.getClaimedTransferredAt(),
                c.getSenderAccountName(),
                c.getWantsPublic(),
                c.getStatus() != null ? c.getStatus().name() : null,
                c.getCreatedAt()
        );
    }
}
