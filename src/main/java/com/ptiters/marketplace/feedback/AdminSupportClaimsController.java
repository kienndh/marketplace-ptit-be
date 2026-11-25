package com.ptiters.marketplace.feedback;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/support-claims")
public class AdminSupportClaimsController {

    private final FeedbackService feedbackService;

    public AdminSupportClaimsController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public ResponseEntity<AdminSupportClaimCursorPage> listSupportClaims(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor) {
        return ResponseEntity.ok(feedbackService.listSupportClaims(limit, cursor));
    }

    @PostMapping("/{claimId}/verify")
    public ResponseEntity<Void> verifySupportClaim(
            @PathVariable Long claimId,
            @Valid @RequestBody AdminSupportClaimVerify request) {
        feedbackService.verifySupportClaim(claimId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{claimId}/reject")
    public ResponseEntity<Void> rejectSupportClaim(
            @PathVariable Long claimId,
            @Valid @RequestBody AdminSupportClaimReject request) {
        feedbackService.rejectSupportClaim(claimId, request);
        return ResponseEntity.noContent().build();
    }
}
