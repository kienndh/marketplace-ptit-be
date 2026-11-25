package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record SupportResponse(
    @JsonProperty("bank_name") String bankName,
    @JsonProperty("account_number") String accountNumber,
    @JsonProperty("account_name") String accountName,
    @JsonProperty("qr_code_url") String qrCodeUrl,
    @JsonProperty("total_contributed") BigDecimal totalContributed
) {}
