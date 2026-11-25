package com.ptiters.marketplace.report;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AdminReportListResponse(
        List<AdminReportResponse> items,
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("has_more") boolean hasMore
) {}
