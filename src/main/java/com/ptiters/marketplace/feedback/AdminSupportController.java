package com.ptiters.marketplace.feedback;

import com.ptiters.marketplace.user.CurrentUserResolver;
import com.ptiters.marketplace.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/support-contributions")
@RequiredArgsConstructor
public class AdminSupportController {

    private final FeedbackService feedbackService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/search-supporters")
    public ResponseEntity<List<SupporterResolveResponse>> searchSupporters(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        currentUserResolver.requireAdmin();
        return ResponseEntity.ok(feedbackService.searchSupporters(query, limit));
    }

    @PostMapping
    public ResponseEntity<AdminSupportContributionItem> createSupportContribution(
            @Valid @RequestBody AdminSupportContributionCreate request
    ) {
        User currentAdmin = currentUserResolver.requireAdmin();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(feedbackService.createSupportContribution(currentAdmin, request));
    }

    @GetMapping
    public ResponseEntity<AdminSupportContributionCursorPage> listSupportContributions(
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "cursor", required = false) String cursor
    ) {
        currentUserResolver.requireAdmin();
        return ResponseEntity.ok(feedbackService.listSupportContributions(limit, cursor));
    }

    @PatchMapping("/{contributionId}/visibility")
    public ResponseEntity<AdminSupportContributionItem> updateSupportContributionVisibility(
            @PathVariable Long contributionId,
            @Valid @RequestBody AdminSupportContributionVisibilityUpdate request
    ) {
        currentUserResolver.requireAdmin();
        return ResponseEntity.ok(feedbackService.updateSupportContributionVisibility(contributionId, request));
    }

    @DeleteMapping("/{contributionId}")
    public ResponseEntity<Void> deleteSupportContribution(@PathVariable Long contributionId) {
        currentUserResolver.requireAdmin();
        feedbackService.deleteSupportContribution(contributionId);
        return ResponseEntity.noContent().build();
    }
}
