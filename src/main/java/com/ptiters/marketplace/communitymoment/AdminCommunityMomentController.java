package com.ptiters.marketplace.communitymoment;

import com.ptiters.marketplace.user.CurrentUserResolver;
import com.ptiters.marketplace.user.User;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/community-moments")
@RequiredArgsConstructor
@Validated
public class AdminCommunityMomentController {

    private final CommunityMomentService communityMomentService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public ResponseEntity<AdminCommunityMomentCursorPage> listAdminCommunityMoments(
            @RequestParam(value = "limit", defaultValue = "20") @Min(1) @Max(50) int limit,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "status", defaultValue = "PENDING") String status) {
        currentUserResolver.requireAdmin();
        return ResponseEntity.ok(communityMomentService.listAdminCommunityMoments(limit, cursor, status));
    }

    @GetMapping("/{momentId}")
    public ResponseEntity<AdminCommunityMomentItem> getAdminCommunityMomentDetail(
            @PathVariable Long momentId) {
        currentUserResolver.requireAdmin();
        return ResponseEntity.ok(communityMomentService.getAdminCommunityMomentDetail(momentId));
    }

    @PostMapping("/{momentId}/approve")
    public ResponseEntity<AdminCommunityMomentItem> approveCommunityMoment(
            @PathVariable Long momentId) {
        User admin = currentUserResolver.requireAdmin();
        return ResponseEntity.ok(communityMomentService.approveCommunityMoment(momentId, admin));
    }

    @PostMapping("/{momentId}/reject")
    public ResponseEntity<CommunityMomentActionResponse> rejectCommunityMoment(
            @PathVariable Long momentId) {
        User admin = currentUserResolver.requireAdmin();
        return ResponseEntity.ok(communityMomentService.rejectCommunityMoment(momentId, admin));
    }

    @DeleteMapping("/{momentId}")
    public ResponseEntity<CommunityMomentActionResponse> deleteCommunityMoment(
            @PathVariable Long momentId) {
        User admin = currentUserResolver.requireAdmin();
        return ResponseEntity.ok(communityMomentService.deleteCommunityMoment(momentId, admin));
    }
}
