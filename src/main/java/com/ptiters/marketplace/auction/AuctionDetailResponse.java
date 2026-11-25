package com.ptiters.marketplace.auction;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AuctionDetailResponse(
        @JsonProperty("auction_id") Long auctionId,
        @JsonProperty("product_id") Long productId,
        @JsonProperty("start_price") BigDecimal startPrice,
        @JsonProperty("step_price") BigDecimal stepPrice,
        @JsonProperty("buy_now_price") BigDecimal buyNowPrice,
        @JsonProperty("current_price") BigDecimal currentPrice,
        String status,
        @JsonProperty("winner_id") Long winnerId,
        @JsonProperty("winner_name") String winnerName,
        @JsonProperty("start_time") LocalDateTime startTime,
        @JsonProperty("end_time") LocalDateTime endTime
) {}
