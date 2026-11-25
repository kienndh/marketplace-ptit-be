package com.ptiters.marketplace.feedback;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/feedback")
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    public AdminFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public ResponseEntity<AdminFeedbackListPage> listAdminFeedbacks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(feedbackService.listAdminFeedbacks(page, size));
    }

    @GetMapping("/{feedbackId}")
    public ResponseEntity<AdminFeedbackDetail> getAdminFeedbackDetail(
            @PathVariable Long feedbackId) {
        return ResponseEntity.ok(feedbackService.getAdminFeedbackDetail(feedbackId));
    }

    @DeleteMapping("/{feedbackId}")
    public ResponseEntity<Void> deleteAdminFeedback(
            @PathVariable Long feedbackId) {
        feedbackService.deleteAdminFeedback(feedbackId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{feedbackId}/evidence/{attachmentId}")
    public ResponseEntity<Object> streamFeedbackEvidence(
            @PathVariable Long feedbackId,
            @PathVariable Long attachmentId) {
        return ResponseEntity.ok(feedbackService.streamFeedbackEvidence(feedbackId, attachmentId));
    }
}
