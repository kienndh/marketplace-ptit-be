package com.ptiters.marketplace.auction;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AuctionBidCursorPage(
        @JsonProperty("items") List<AuctionBidResponse> items,
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("has_more") boolean hasMore
) {
}
