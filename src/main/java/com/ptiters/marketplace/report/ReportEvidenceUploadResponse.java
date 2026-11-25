package com.ptiters.marketplace.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ptiters.marketplace.report.ReportSummaries.ReportEvidenceMetadata;

import java.util.List;

public record ReportEvidenceUploadResponse(
        @JsonProperty("report_id") Long reportId,
        List<ReportEvidenceMetadata> attachments
) {}
