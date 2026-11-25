package com.ptiters.marketplace.auction;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Python: {@code AuctionBidResponse} — ẩn danh người đặt giá ({@code bidder_display}), không lộ bidder_id. */
public record AuctionBidResponse(
        @JsonProperty("bid_id") Long bidId,
        @JsonProperty("auction_id") Long auctionId,
        @JsonProperty("bidder_display") String bidderDisplay,
        @JsonProperty("bidder_avatar_url") String bidderAvatarUrl,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("is_current_user") boolean isCurrentUser,
        @JsonProperty("is_highest") boolean isHighest
) {
}
