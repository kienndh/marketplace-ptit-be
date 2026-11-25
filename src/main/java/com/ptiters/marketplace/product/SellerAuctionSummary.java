package com.ptiters.marketplace.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SellerAuctionSummary(
        @JsonProperty("auction_id") Long auctionId,
        String status,
        @JsonProperty("starting_price") BigDecimal startingPrice,
        @JsonProperty("bid_step") BigDecimal bidStep,
        @JsonProperty("current_price") BigDecimal currentPrice,
        @JsonProperty("starts_at") LocalDateTime startsAt,
        @JsonProperty("ends_at") LocalDateTime endsAt,
        @JsonProperty("winner_checkout_deadline") LocalDateTime winnerCheckoutDeadline,
        @JsonProperty("bid_count") int bidCount
) {}
