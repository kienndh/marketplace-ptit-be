package com.ptiters.marketplace.auction;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AuctionBidResponse(
        @JsonProperty("bid_id") Long bidId,
        @JsonProperty("auction_id") Long auctionId,
        @JsonProperty("bidder_id") Long bidderId,
        @JsonProperty("bidder_name") String bidderName,
        BigDecimal amount,
        @JsonProperty("bid_time") LocalDateTime bidTime
) {}
