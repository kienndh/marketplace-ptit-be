package com.ptiters.marketplace.report;

import java.util.List;
import java.util.Optional;

public interface ReportAttachmentRepository {
    ReportAttachment save(ReportAttachment attachment);
    Optional<ReportAttachment> findById(Long id);
    List<ReportAttachment> findByReportId(Long reportId);
}
