package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record AdminSupportContributionItem(
        @JsonProperty("contribution_id") Long contributionId,
        AdminUserSafeIdentity supporter,
        @JsonProperty("amount_vnd") Long amountVnd,
        @JsonProperty("is_public") Boolean isPublic,
        @JsonProperty("received_at") OffsetDateTime receivedAt,
        @JsonProperty("transaction_ref") String transactionRef,
        @JsonProperty("admin_note") String adminNote,
        @JsonProperty("recorded_by") AdminUserSafeIdentity recordedBy,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {}
