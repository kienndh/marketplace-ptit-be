package com.ptiters.marketplace.favorite;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record FavoriteListResponse(
        List<FavoriteProductResponse> items,
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("has_more") boolean hasMore
) {}
