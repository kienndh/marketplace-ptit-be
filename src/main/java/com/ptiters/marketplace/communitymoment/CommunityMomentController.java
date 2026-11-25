package com.ptiters.marketplace.communitymoment;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/community-moments")
public class CommunityMomentController {

    private final CommunityMomentService communityMomentService;

    public CommunityMomentController(CommunityMomentService communityMomentService) {
        this.communityMomentService = communityMomentService;
    }

    @PostMapping
    public ResponseEntity<CommunityMomentSubmissionResponse> submitCommunityMoment(
            @RequestParam("image") Object imageFile,
            @RequestParam(value = "note", required = false) String note,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED).body(communityMomentService.submitCommunityMoment(null, imageFile, note));
    }

    @GetMapping("/hero")
    public ResponseEntity<PublicHeroResponse> getPublicHeroMoments() {
        return ResponseEntity.ok(communityMomentService.getPublicHeroMoments());
    }
}
