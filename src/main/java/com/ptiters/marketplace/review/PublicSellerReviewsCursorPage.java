package com.ptiters.marketplace.review;

import java.util.List;

public record PublicSellerReviewsCursorPage(
        List<PublicSellerReviewItem> items,
        String nextCursor,
        boolean hasMore
) {}
