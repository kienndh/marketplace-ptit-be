package com.ptiters.marketplace.order;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrderPaymentBankResponse(
        @JsonProperty("bank_bin") String bankBin,
        @JsonProperty("bank_code") String bankCode,
        @JsonProperty("bank_name") String bankName,
        @JsonProperty("bank_short_name") String bankShortName,
        @JsonProperty("account_number") String accountNumber,
        @JsonProperty("account_name") String accountName,
        @JsonProperty("qr_url") String qrUrl
) {
}
