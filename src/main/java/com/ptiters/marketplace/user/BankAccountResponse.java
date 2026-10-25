package com.ptiters.marketplace.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BankAccountResponse {

    @JsonProperty("bank_bin")
    private String bankBin;
    @JsonProperty("bank_code")
    private String bankCode;
    @JsonProperty("bank_name")
    private String bankName;
    @JsonProperty("bank_short_name")
    private String bankShortName;
    @JsonProperty("account_number")
    private String accountNumber;
    @JsonProperty("account_name")
    private String accountName;
}