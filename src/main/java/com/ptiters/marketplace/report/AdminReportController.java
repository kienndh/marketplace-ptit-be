package com.ptiters.marketplace.report;

import tools.jackson.databind.JsonNode;
import com.ptiters.marketplace.user.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminReportService adminReportService;
    private final ReportEvidenceStreamer evidenceStreamer;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public ResponseEntity<AdminReportListResponse> listAdminReports(
            @RequestParam(name = "queue_state", required = false) AdminReportQueueState queueState,
            @RequestParam(name = "status", required = false) ReportStatus status,
            @RequestParam(name = "workflow", required = false) ReportWorkflowState workflow,
            @RequestParam(name = "conclusion", required = false) ReportConclusion conclusion,
            @RequestParam(name = "target_type", required = false) ReportTargetType targetType,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        currentUserResolver.requireAdmin();
        return ResponseEntity.ok(adminReportService.listReports(limit, cursor, queueState, status, workflow, conclusion, targetType));
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<AdminReportDetailResponse> getAdminReportDetail(@PathVariable("reportId") Long reportId) {
        var admin = currentUserResolver.requireAdmin();
        return ResponseEntity.ok(adminReportService.getDetail(admin, reportId));
    }

    @PatchMapping("/{reportId}/status")
    public ResponseEntity<AdminReportDetailResponse> updateAdminReportStatus(
            @PathVariable("reportId") Long reportId, @RequestBody JsonNode payload) {
        var admin = currentUserResolver.requireAdmin();
        var request = AdminReportStatusUpdateRequest.parse(payload);
        return ResponseEntity.ok(adminReportService.updateStatus(admin, reportId, request.status()));
    }

    @PostMapping("/{reportId}/enforcement")
    public ResponseEntity<AdminReportDetailResponse> enforceAdminReportAction(
            @PathVariable("reportId") Long reportId, @RequestBody JsonNode payload) {
        var admin = currentUserResolver.requireAdmin();
        var request = AdminEnforcementRequest.parse(payload);
        return ResponseEntity.ok(adminReportService.enforce(admin, reportId, request));
    }

    @GetMapping("/{reportId}/evidence/{attachmentId}")
    public ResponseEntity<StreamingResponseBody> getAdminReportEvidence(
            @PathVariable("reportId") Long reportId,
            @PathVariable("attachmentId") Long attachmentId,
            @RequestParam(name = "variant", defaultValue = "thumbnail") String variant,
            @RequestHeader(name = HttpHeaders.RANGE, required = false) String range) {
        currentUserResolver.requireAdmin();
        var target = adminReportService.resolveEvidence(reportId, attachmentId, variant);
        var opened = evidenceStreamer.open(target.url(), target.video(), range);

        String name = target.originalName() == null ? "evidence_" + attachmentId : target.originalName();
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        var response = ResponseEntity.status(HttpStatusCode.valueOf(opened.status()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name.replace("\"", "") + "\"; filename*=UTF-8''" + encoded)
                .header(HttpHeaders.CONTENT_TYPE, target.mimeType() != null ? target.mimeType() : (target.video() ? "video/mp4" : "image/jpeg"))
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store");
        if (target.video()) {
            response.header(HttpHeaders.ACCEPT_RANGES, "bytes");
            if (opened.contentRange() != null) {
                response.header(HttpHeaders.CONTENT_RANGE, opened.contentRange());
            }
        }
        if (opened.contentLength() != null) {
            response.header(HttpHeaders.CONTENT_LENGTH, opened.contentLength());
        }
        return response.body(out -> {
            try (InputStream in = opened.body()) {
                in.transferTo(out);
            }
        });
    }
}
