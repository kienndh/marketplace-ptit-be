package com.ptiters.marketplace.report;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reports")
public class AdminReportController {

    private final ReportService reportService;

    public AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public ResponseEntity<AdminReportListResponse> listAdminReports(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(reportService.listAdminReports(page, size, status));
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<AdminReportDetailResponse> getAdminReportDetail(
            @PathVariable Long reportId) {
        return ResponseEntity.ok(reportService.getAdminReportDetail(reportId));
    }

    @PatchMapping("/{reportId}/status")
    public ResponseEntity<AdminReportDetailResponse> updateAdminReportStatus(
            @PathVariable Long reportId,
            @Valid @RequestBody AdminReportStatusUpdateRequest request) {
        return ResponseEntity.ok(reportService.updateReportStatus(reportId, request));
    }

    @PostMapping("/{reportId}/enforcement")
    public ResponseEntity<Void> enforceAdminReportAction(
            @PathVariable Long reportId,
            @Valid @RequestBody AdminEnforcementRequest request) {
        reportService.enforceAction(reportId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{reportId}/evidence/{attachmentId}")
    public ResponseEntity<Object> getAdminReportEvidence(
            @PathVariable Long reportId,
            @PathVariable Long attachmentId) {
        return ResponseEntity.ok(reportService.getEvidence(reportId, attachmentId));
    }
}
