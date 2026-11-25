package com.ptiters.marketplace.feedback;

import com.ptiters.marketplace.user.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/feedback")
@RequiredArgsConstructor
public class AdminFeedbackController {

    private final FeedbackService feedbackService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public ResponseEntity<AdminFeedbackListPage> listAdminFeedbacks(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "20") int limit
    ) {
        currentUserResolver.requireAdmin();
        return ResponseEntity.ok(feedbackService.listAdminFeedbacks(limit, cursor));
    }

    @GetMapping("/{feedbackId}")
    public ResponseEntity<AdminFeedbackDetail> getAdminFeedbackDetail(@PathVariable Long feedbackId) {
        currentUserResolver.requireAdmin();
        return ResponseEntity.ok(feedbackService.getAdminFeedbackDetail(feedbackId));
    }

    @DeleteMapping("/{feedbackId}")
    public ResponseEntity<Void> deleteAdminFeedback(@PathVariable Long feedbackId) {
        currentUserResolver.requireAdmin();
        feedbackService.deleteAdminFeedback(feedbackId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{feedbackId}/evidence/{attachmentId}")
    public ResponseEntity<Resource> streamFeedbackEvidence(
            @PathVariable Long feedbackId,
            @PathVariable Long attachmentId,
            @RequestHeader(value = "Range", required = false) String rangeHeader
    ) {
        currentUserResolver.requireAdmin();
        FeedbackService.EvidenceStreamResult result =
                feedbackService.streamFeedbackEvidence(feedbackId, attachmentId, rangeHeader);
        return ResponseEntity.status(result.statusCode())
                .headers(result.headers())
                .body(result.body());
    }
}
