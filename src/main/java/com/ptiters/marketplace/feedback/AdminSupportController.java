package com.ptiters.marketplace.feedback;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/support-contributions")
public class AdminSupportController {

    private final FeedbackService feedbackService;

    public AdminSupportController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping("/search-supporters")
    public ResponseEntity<Object> searchSupporters(
            @RequestParam String query) {
        return ResponseEntity.ok(feedbackService.searchSupporters(query));
    }

    @PostMapping
    public ResponseEntity<AdminSupportContributionItem> createSupportContribution(
            @Valid @RequestBody AdminSupportContributionCreate request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(feedbackService.createSupportContribution(request));
    }

    @GetMapping
    public ResponseEntity<AdminSupportContributionCursorPage> listSupportContributions(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor) {
        return ResponseEntity.ok(feedbackService.listSupportContributions(limit, cursor));
    }

    @PatchMapping("/{contributionId}/visibility")
    public ResponseEntity<Void> updateSupportContributionVisibility(
            @PathVariable Long contributionId,
            @Valid @RequestBody AdminSupportContributionVisibilityUpdate request) {
        feedbackService.updateSupportContributionVisibility(contributionId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{contributionId}")
    public ResponseEntity<Void> deleteSupportContribution(
            @PathVariable Long contributionId) {
        feedbackService.deleteSupportContribution(contributionId);
        return ResponseEntity.noContent().build();
    }
}
