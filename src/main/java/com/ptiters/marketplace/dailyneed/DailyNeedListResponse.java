package com.ptiters.marketplace.dailyneed;

import java.util.List;

public record DailyNeedListResponse(
    List<DailyNeedResponse> items
) {}
