package com.ptiters.marketplace.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ProductListResponse(
        List<ProductSummaryResponse> items,
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("has_more") boolean hasMore
) {}
