package com.ptiters.marketplace.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateBankAccountRequest {

    @JsonProperty("bank_bin")
    @NotBlank(message = "bank_bin must not be blank")
    private String bankBin;

    @JsonProperty("account_number")
    @NotBlank(message = "account_number must not be blank")
    @Pattern(regexp = "^[A-Za-z0-9]{4,30}$",
            message = "account_number must be 4-30 alphanumeric characters")
    private String accountNumber;

    @JsonProperty("account_name")
    @NotBlank(message = "account_name must not be blank")
    @Size(min = 2, max = 100, message = "account_name must be 2-100 characters")
    private String accountName;
}