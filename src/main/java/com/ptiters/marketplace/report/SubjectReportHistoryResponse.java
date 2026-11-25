package com.ptiters.marketplace.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ptiters.marketplace.report.ReportSummaries.SubjectReportHistoryItem;

import java.util.List;

public record SubjectReportHistoryResponse(
        @JsonProperty("user_id") Long userId,
        List<SubjectReportHistoryItem> items,
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("has_more") boolean hasMore,
        @JsonProperty("total_count") long totalCount
) {}
