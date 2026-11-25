package com.ptiters.marketplace.auction;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WonAuctionItemResponse(
        @JsonProperty("auction_id") Long auctionId,
        @JsonProperty("product_id") Long productId,
        @JsonProperty("product_title") String productTitle,
        @JsonProperty("winning_bid") BigDecimal winningBid,
        @JsonProperty("ended_at") LocalDateTime endedAt
) {}
