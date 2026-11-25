package com.ptiters.marketplace.report;

import tools.jackson.databind.JsonNode;
import com.ptiters.marketplace.exception.ApiException;
import com.ptiters.marketplace.user.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping
    public ResponseEntity<ReportResponse> createReport(@RequestBody JsonNode payload) {
        var user = currentUserResolver.getCurrentActiveUser();
        ReportCreateRequest request = ReportCreateRequest.parse(payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.createReport(user, request));
    }

    @PostMapping(value = "/{reportId}/evidence", consumes = "multipart/form-data")
    public ResponseEntity<ReportEvidenceUploadResponse> uploadReportEvidence(
            @PathVariable("reportId") Long reportId,
            @RequestParam(name = "files", required = false) List<MultipartFile> files) {
        var user = currentUserResolver.getCurrentActiveUser();
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Vui lòng chọn ít nhất một tệp minh chứng.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.uploadEvidence(user, reportId, files));
    }
}
