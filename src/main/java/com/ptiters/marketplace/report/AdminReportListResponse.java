package com.ptiters.marketplace.report;

import java.util.List;

public record AdminReportListResponse(
    List<AdminReportResponse> items,
    long total,
    int page,
    int size
) {}
