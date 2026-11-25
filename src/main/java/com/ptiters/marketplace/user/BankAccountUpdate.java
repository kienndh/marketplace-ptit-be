package com.ptiters.marketplace.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountUpdate {

    @NotBlank(message = "Mã ngân hàng không được để trống.")
    @Size(min = 2, max = 20, message = "Mã ngân hàng từ 2 đến 20 ký tự.")
    @JsonProperty("bank_bin")
    private String bankBin;

    @NotBlank(message = "Số tài khoản không được để trống.")
    @Size(min = 4, max = 30, message = "Độ dài số tài khoản không hợp lệ (4 - 30 chữ số).")
    @JsonProperty("account_number")
    private String accountNumber;

    @NotBlank(message = "Tên chủ tài khoản không được để trống.")
    @Size(min = 2, max = 100, message = "Tên chủ tài khoản phải từ 2 đến 100 ký tự.")
    @JsonProperty("account_name")
    private String accountName;
}
