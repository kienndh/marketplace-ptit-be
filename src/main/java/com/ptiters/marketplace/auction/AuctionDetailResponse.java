package com.ptiters.marketplace.auction;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Python: {@code AuctionDetailResponse}. Tuyệt đối KHÔNG có {@code winner_id}; dùng các cờ ngữ cảnh
 * ({@code has_winner}, {@code is_current_user_winner}, {@code is_current_user_highest_bidder}, {@code winner_checkout_state}).
 */
public record AuctionDetailResponse(
        @JsonProperty("auction_id") Long auctionId,
        @JsonProperty("product_id") Long productId,
        @JsonProperty("status") String status,
        @JsonProperty("starting_price") BigDecimal startingPrice,
        @JsonProperty("bid_step") BigDecimal bidStep,
        @JsonProperty("current_price") BigDecimal currentPrice,
        @JsonProperty("highest_bid") BigDecimal highestBid,
        @JsonProperty("bid_count") long bidCount,
        @JsonProperty("minimum_next_bid") BigDecimal minimumNextBid,
        @JsonProperty("starts_at") LocalDateTime startsAt,
        @JsonProperty("ends_at") LocalDateTime endsAt,
        @JsonProperty("has_winner") boolean hasWinner,
        @JsonProperty("final_price") BigDecimal finalPrice,
        @JsonProperty("winner_checkout_deadline") LocalDateTime winnerCheckoutDeadline,
        @JsonProperty("is_current_user_winner") boolean isCurrentUserWinner,
        @JsonProperty("is_current_user_highest_bidder") boolean isCurrentUserHighestBidder,
        @JsonProperty("highest_bidder_display") String highestBidderDisplay,
        @JsonProperty("server_time") LocalDateTime serverTime,
        @JsonProperty("winner_checkout_state") String winnerCheckoutState,
        @JsonProperty("winner_order_id") Long winnerOrderId
) {
}
