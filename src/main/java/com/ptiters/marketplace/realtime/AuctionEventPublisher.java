package com.ptiters.marketplace.realtime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Broadcast sự kiện phòng đấu giá. Tương ứng {@code publish_auction_event(auction_id, event_data)}
 * ({@code app/modules/auctions/websocket.py}).
 *
 * <p>Điểm mở rộng duy nhất là {@link #publishAuctionEvent}; các hàm tiện ích dựng payload y hệt Python
 * (AUCTION_STARTED, BID_PLACED, AUCTION_ENDED, AUCTION_CANCELLED). Gọi SAU commit, best-effort.
 */
public interface AuctionEventPublisher {

    void publishAuctionEvent(Long auctionId, Map<String, Object> eventData);

    default void publishAuctionStarted(Long auctionId, BigDecimal startingPrice, Instant serverTime) {
        Map<String, Object> e = base("AUCTION_STARTED", auctionId, serverTime);
        e.put("current_price", String.valueOf(startingPrice));
        e.put("bid_count", 0);
        publishAuctionEvent(auctionId, e);
    }

    default void publishBidPlaced(Long auctionId, BigDecimal amount, BigDecimal bidStep, long bidCount, Instant serverTime) {
        Map<String, Object> e = base("BID_PLACED", auctionId, serverTime);
        e.put("current_price", String.valueOf(amount));
        e.put("highest_bid", String.valueOf(amount));
        e.put("bid_count", bidCount);
        e.put("minimum_next_bid", String.valueOf(amount.add(bidStep)));
        publishAuctionEvent(auctionId, e);
    }

    /** {@code finalPrice} = null khi không có người thắng. */
    default void publishAuctionEnded(Long auctionId, BigDecimal finalPrice, Instant serverTime) {
        Map<String, Object> e = base("AUCTION_ENDED", auctionId, serverTime);
        e.put("status", "ENDED");
        e.put("final_price", finalPrice == null ? null : String.valueOf(finalPrice));
        e.put("has_winner", finalPrice != null);
        publishAuctionEvent(auctionId, e);
    }

    default void publishAuctionCancelled(Long auctionId, Instant serverTime) {
        publishAuctionEvent(auctionId, base("AUCTION_CANCELLED", auctionId, serverTime));
    }

    private static Map<String, Object> base(String event, Long auctionId, Instant serverTime) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("event", event);
        e.put("auction_id", auctionId);
        e.put("server_time", serverTime.toString());
        return e;
    }
}
