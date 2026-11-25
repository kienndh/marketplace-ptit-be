package com.ptiters.marketplace.review;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PublicSellerReviewsCursorPage(
        List<PublicSellerReviewItem> items,
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("has_more") boolean hasMore
) {}
