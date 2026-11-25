package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record AdminSupportContributionCreate(
        @NotNull(message = "supporter_id không được để trống.")
        @JsonProperty("supporter_id") Long supporterId,

        @NotNull(message = "Số tiền ủng hộ không được để trống.")
        @Min(value = 1, message = "Số tiền ủng hộ phải lớn hơn 0.")
        @Max(value = 1_000_000_000L, message = "Số tiền ủng hộ không được vượt quá 1,000,000,000.")
        @JsonProperty("amount_vnd") Long amountVnd,

        @NotNull(message = "received_at không được để trống.")
        @JsonProperty("received_at") OffsetDateTime receivedAt,

        @JsonProperty("is_public") Boolean isPublic,

        @Size(max = 100, message = "transaction_ref tối đa 100 ký tự.")
        @JsonProperty("transaction_ref") String transactionRef,

        @Size(max = 255, message = "admin_note tối đa 255 ký tự.")
        @JsonProperty("admin_note") String adminNote
) {}
