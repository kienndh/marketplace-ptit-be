package com.ptiters.marketplace.feedback;

import com.ptiters.marketplace.user.CurrentUserResolver;
import com.ptiters.marketplace.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping
    public ResponseEntity<FeedbackCreateResponse> submitFeedback(
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "images", required = false) List<MultipartFile> images,
            @RequestParam(value = "video", required = false) MultipartFile video
    ) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(feedbackService.submitFeedback(currentUser, content, images, video));
    }

    @GetMapping
    public ResponseEntity<PublicFeedbackCursorPage> listPublicFeedbacks(
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "cursor", required = false) String cursor
    ) {
        return ResponseEntity.ok(feedbackService.listPublicFeedbacks(limit, cursor));
    }

    @GetMapping("/me")
    public ResponseEntity<MyFeedbackCursorPage> listMyFeedbacks(
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "cursor", required = false) String cursor
    ) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        return ResponseEntity.ok(feedbackService.listMyFeedbacks(currentUser, limit, cursor));
    }

    @DeleteMapping("/{feedbackId}")
    public ResponseEntity<Void> deleteMyFeedback(@PathVariable Long feedbackId) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        feedbackService.deleteMyFeedback(currentUser, feedbackId);
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
            @Valid @RequestBody SupportClaimCreate request
    ) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(feedbackService.submitSupportClaim(currentUser, request));
    }

    @GetMapping("/support/claims/me")
    public ResponseEntity<SupportClaimUserListPage> listMySupportClaims(
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        return ResponseEntity.ok(feedbackService.listMySupportClaims(currentUser, limit));
    }
}
