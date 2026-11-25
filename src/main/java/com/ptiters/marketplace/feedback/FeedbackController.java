package com.ptiters.marketplace.feedback;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public ResponseEntity<FeedbackCreateResponse> submitFeedback(
            @Valid @RequestBody FeedbackCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED).body(feedbackService.submitFeedback(null, request));
    }

    @GetMapping
    public ResponseEntity<PublicFeedbackCursorPage> listPublicFeedbacks(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor) {
        return ResponseEntity.ok(feedbackService.listPublicFeedbacks(limit, cursor));
    }

    @GetMapping("/me")
    public ResponseEntity<MyFeedbackCursorPage> listMyFeedbacks(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(feedbackService.listMyFeedbacks(null, limit, cursor));
    }

    @DeleteMapping("/{feedbackId}")
    public ResponseEntity<Void> deleteMyFeedback(
            @PathVariable Long feedbackId,
            @AuthenticationPrincipal UserDetails userDetails) {
        feedbackService.deleteMyFeedback(null, feedbackId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/support/leaderboard")
    public ResponseEntity<SupporterLeaderboardResponse> getSupporterLeaderboard() {
        return ResponseEntity.ok(feedbackService.getSupporterLeaderboard());
    }

    @GetMapping("/support")
    public ResponseEntity<SupportResponse> getSupportInfo() {
        return ResponseEntity.ok(feedbackService.getSupportInfo());
    }

    @PostMapping("/support/claims")
    public ResponseEntity<SupportClaimUserItem> submitSupportClaim(
            @Valid @RequestBody SupportClaimCreate request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED).body(feedbackService.submitSupportClaim(null, request));
    }

    @GetMapping("/support/claims/me")
    public ResponseEntity<SupportClaimUserListPage> listMySupportClaims(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(feedbackService.listMySupportClaims(null));
    }
}
