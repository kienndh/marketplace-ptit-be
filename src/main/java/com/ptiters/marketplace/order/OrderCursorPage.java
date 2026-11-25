package com.ptiters.marketplace.order;

import java.util.List;

public record OrderCursorPage(
        List<OrderSummaryResponse> items,
        String nextCursor,
        boolean hasMore
) {}
