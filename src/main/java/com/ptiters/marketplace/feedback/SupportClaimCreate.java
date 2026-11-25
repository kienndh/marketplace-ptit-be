package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record SupportClaimCreate(
        @NotNull(message = "Số tiền đã chuyển không được để trống.")
        @Min(value = 1, message = "Số tiền đã chuyển phải lớn hơn 0.")
        @Max(value = 1_000_000_000L, message = "Số tiền đã chuyển không được vượt quá 1,000,000,000.")
        @JsonProperty("claimed_amount_vnd") Long claimedAmountVnd,

        @NotNull(message = "Thời gian chuyển tiền không được để trống.")
        @JsonProperty("claimed_transferred_at") OffsetDateTime claimedTransferredAt,

        @NotBlank(message = "Tên chủ tài khoản chuyển tiền không được để trống")
        @Size(max = 100, message = "Tên chủ tài khoản chuyển tiền tối đa 100 ký tự.")
        @JsonProperty("sender_account_name") String senderAccountName,

        @JsonProperty("wants_public") Boolean wantsPublic
) {}
