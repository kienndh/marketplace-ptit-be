package com.ptiters.marketplace.communitymoment;

import com.ptiters.marketplace.user.CurrentUserResolver;
import com.ptiters.marketplace.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/community-moments")
@RequiredArgsConstructor
public class CommunityMomentController {

    private final CommunityMomentService communityMomentService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping
    public ResponseEntity<CommunityMomentSubmissionResponse> submitCommunityMoment(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "note", required = false) String note) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        CommunityMomentSubmissionResponse response = communityMomentService.submitCommunityMoment(currentUser, file, note);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/hero")
    public ResponseEntity<PublicHeroResponse> getPublicHeroMoments() {
        return ResponseEntity.ok(communityMomentService.getPublicHeroMoments());
    }

    @ExceptionHandler(CommunityMomentPendingException.class)
    public ResponseEntity<Map<String, Object>> handlePendingConflict(CommunityMomentPendingException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                Map.of("detail", Map.of(
                        "code", CommunityMomentPendingException.CODE,
                        "message", ex.getMessage()
                ))
        );
    }
}
