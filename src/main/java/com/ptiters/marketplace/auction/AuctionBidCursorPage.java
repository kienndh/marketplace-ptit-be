package com.ptiters.marketplace.auction;

import java.util.List;

public record AuctionBidCursorPage(
        List<AuctionBidResponse> items,
        String nextCursor,
        boolean hasMore
) {}
