package com.ptiters.marketplace.report;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    public ResponseEntity<ReportResponse> createReport(
            @Valid @RequestBody ReportCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.createReport(null, request));
    }

    @PostMapping("/{reportId}/evidence")
    public ResponseEntity<ReportEvidenceUploadResponse> uploadReportEvidence(
            @PathVariable Long reportId,
            @RequestParam("file") Object file,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(reportService.uploadEvidence(reportId, file));
    }
}
