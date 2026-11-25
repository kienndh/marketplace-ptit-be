package com.ptiters.marketplace.feedback;

import com.ptiters.marketplace.user.CurrentUserResolver;
import com.ptiters.marketplace.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/support-claims")
@RequiredArgsConstructor
public class AdminSupportClaimsController {

    private final FeedbackService feedbackService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public ResponseEntity<AdminSupportClaimCursorPage> listSupportClaims(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "cursor", required = false) String cursor
    ) {
        currentUserResolver.requireAdmin();
        return ResponseEntity.ok(feedbackService.listSupportClaims(status, limit, cursor));
    }

    @PostMapping("/{claimId}/verify")
    public ResponseEntity<AdminSupportClaimItem> verifySupportClaim(
            @PathVariable Long claimId,
            @Valid @RequestBody AdminSupportClaimVerify request
    ) {
        User currentAdmin = currentUserResolver.requireAdmin();
        return ResponseEntity.ok(feedbackService.verifySupportClaim(claimId, currentAdmin, request));
    }

    @PostMapping("/{claimId}/reject")
    public ResponseEntity<AdminSupportClaimItem> rejectSupportClaim(
            @PathVariable Long claimId,
            @Valid @RequestBody(required = false) AdminSupportClaimReject request
    ) {
        User currentAdmin = currentUserResolver.requireAdmin();
        return ResponseEntity.ok(feedbackService.rejectSupportClaim(claimId, currentAdmin, request));
    }
}
