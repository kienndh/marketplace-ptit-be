package com.ptiters.marketplace.auction;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AuctionCreateRequest(
        @JsonProperty("product_id") Long productId,
        @JsonProperty("start_price") BigDecimal startPrice,
        @JsonProperty("step_price") BigDecimal stepPrice,
        @JsonProperty("buy_now_price") BigDecimal buyNowPrice,
        @JsonProperty("start_time") LocalDateTime startTime,
        @JsonProperty("end_time") LocalDateTime endTime
) {}
