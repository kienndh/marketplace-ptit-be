package com.ptiters.marketplace.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final CurrentUserResolver currentUserResolver;
    private final UserService userService;
    private final CapabilityService capabilityService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyProfile() {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        return ResponseEntity.ok(UserResponse.from(currentUser));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMyProfile(@Valid @RequestBody UserUpdate payload) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        User updatedUser = userService.updateProfile(currentUser, payload);
        return ResponseEntity.ok(UserResponse.from(updatedUser));
    }

    @GetMapping("/me/access-status")
    public ResponseEntity<UserAccessStatusResponse> getMyAccessStatus() {
        User currentUser = currentUserResolver.getCurrentUser();
        return ResponseEntity.ok(capabilityService.getUserAccessStatus(currentUser));
    }

    @GetMapping("/banks")
    public ResponseEntity<List<SupportedBank>> getSupportedBanks() {
        return ResponseEntity.ok(SupportedBanksRegistry.getAllBanks());
    }

    @GetMapping("/me/bank-account")
    public ResponseEntity<BankAccountResponse> getMyBankAccount() {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        return ResponseEntity.ok(userService.getBankAccount(currentUser));
    }

    @PutMapping("/me/bank-account")
    public ResponseEntity<BankAccountResponse> updateMyBankAccount(@Valid @RequestBody BankAccountUpdate payload) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        BankAccountResponse response = userService.updateBankAccount(currentUser, payload);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/me/bank-account")
    public ResponseEntity<Void> deleteMyBankAccount() {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        userService.deleteBankAccount(currentUser);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<PublicUserProfileResponse> getPublicUserProfile(@PathVariable("publicId") UUID publicId) {
        PublicUserProfileResponse profile = userService.getPublicProfile(publicId);
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/{publicId}/sold-products")
    public ResponseEntity<SoldProductListResponse> getUserSoldProducts(@PathVariable("publicId") UUID publicId) {
        SoldProductListResponse soldProducts = userService.getSoldProducts(publicId);
        return ResponseEntity.ok(soldProducts);
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserSearchResponse>> searchUsers(
            @RequestParam("q") @Size(min = 2) String query,
            @RequestParam(value = "limit", defaultValue = "20") @Min(1) @Max(50) int limit,
            @RequestParam(value = "conversation_id", required = false) Long conversationId) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        return ResponseEntity.ok(userService.searchUsers(currentUser, query, limit, conversationId));
    }
}
