package com.ptiters.marketplace.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SubjectReportHistoryResponse(
    @JsonProperty("target_id") Long targetId,
    @JsonProperty("target_type") String targetType,
    @JsonProperty("total_reports") int totalReports,
    List<AdminReportResponse> history
) {}
