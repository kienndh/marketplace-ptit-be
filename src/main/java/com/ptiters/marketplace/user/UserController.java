package com.ptiters.marketplace.user;

import com.ptiters.marketplace.bank.Bank;
import com.ptiters.marketplace.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@AllArgsConstructor
public class UserController {

    private UserService userService;
    private BankAccountService bankAccountService;

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return userService.getById(principal.getUserId());
    }

    @PatchMapping("/me")
    public UserResponse updateMe(@AuthenticationPrincipal AuthenticatedUser principal,
                                 @Valid @RequestBody UpdateUserRequest request) {
        return userService.updateMe(principal.getUserId(), request);
    }

    // Công khai: danh sách ngân hàng hỗ trợ
    @GetMapping("/banks")
    public List<Bank> banks() {
        return bankAccountService.getSupportedBanks();
    }

    @GetMapping("/me/bank-account")
    public ResponseEntity<Object> getBankAccount(@AuthenticationPrincipal AuthenticatedUser principal) {
        BankAccountResponse response = bankAccountService.getBankAccount(principal.getUserId());
        if (response == null) {
            // Contract: chưa cấu hình thì trả JSON null với HTTP 200
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("null");
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}")
    public PublicUserResponse publicProfile(@PathVariable("userId") Long userId) {
        return userService.getPublicProfile(userId);
    }

    @PutMapping("/me/bank-account")
    public BankAccountResponse saveBankAccount(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @Valid @RequestBody UpdateBankAccountRequest request) {
        return bankAccountService.saveBankAccount(principal.getUserId(), request);
    }

    @DeleteMapping("/me/bank-account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBankAccount(@AuthenticationPrincipal AuthenticatedUser principal) {
        bankAccountService.deleteBankAccount(principal.getUserId());
    }
}