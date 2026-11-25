package com.ptiters.marketplace.report;

import org.springframework.stereotype.Service;

@Service
public class ReportService {

    public ReportResponse createReport(Long reporterId, ReportCreateRequest request) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public ReportEvidenceUploadResponse uploadEvidence(Long reportId, Object file) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public AdminReportListResponse listAdminReports(int page, int size, String status) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public AdminReportDetailResponse getAdminReportDetail(Long reportId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public AdminReportDetailResponse updateReportStatus(Long reportId, AdminReportStatusUpdateRequest request) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public void enforceAction(Long reportId, AdminEnforcementRequest request) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public Object getEvidence(Long reportId, Long attachmentId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }
}
