package com.ptiters.marketplace.report;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReportEvidenceUploadResponse(
    @JsonProperty("attachment_id") Long attachmentId,
    @JsonProperty("file_url") String fileUrl
) {}
