package com.ptiters.marketplace.communitymoment;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/community-moments")
public class AdminCommunityMomentController {

    private final CommunityMomentService communityMomentService;

    public AdminCommunityMomentController(CommunityMomentService communityMomentService) {
        this.communityMomentService = communityMomentService;
    }

    @GetMapping
    public ResponseEntity<AdminCommunityMomentCursorPage> listAdminCommunityMoments(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(communityMomentService.listAdminCommunityMoments(limit, cursor, status));
    }

    @GetMapping("/{momentId}")
    public ResponseEntity<AdminCommunityMomentItem> getAdminCommunityMomentDetail(
            @PathVariable Long momentId) {
        return ResponseEntity.ok(communityMomentService.getAdminCommunityMomentDetail(momentId));
    }

    @PostMapping("/{momentId}/approve")
    public ResponseEntity<CommunityMomentActionResponse> approveCommunityMoment(
            @PathVariable Long momentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(communityMomentService.approveCommunityMoment(momentId, null));
    }

    @PostMapping("/{momentId}/reject")
    public ResponseEntity<CommunityMomentActionResponse> rejectCommunityMoment(
            @PathVariable Long momentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(communityMomentService.rejectCommunityMoment(momentId, null));
    }

    @DeleteMapping("/{momentId}")
    public ResponseEntity<CommunityMomentActionResponse> deleteCommunityMoment(
            @PathVariable Long momentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(communityMomentService.deleteCommunityMoment(momentId, null));
    }
}
