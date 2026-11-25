package com.ptiters.marketplace.auction;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WonAuctionItemResponse(
        @JsonProperty("auction_id") Long auctionId,
        @JsonProperty("product_id") Long productId,
        @JsonProperty("product_title") String productTitle,
        @JsonProperty("product_image_url") String productImageUrl,
        @JsonProperty("final_price") BigDecimal finalPrice,
        @JsonProperty("winner_checkout_deadline") LocalDateTime winnerCheckoutDeadline,
        @JsonProperty("server_time") LocalDateTime serverTime
) {
}
