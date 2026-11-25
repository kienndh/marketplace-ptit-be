package com.ptiters.marketplace.realtime;

import java.util.Map;

/** Bản mặc định an toàn: không broadcast. */
public class NoOpAuctionEventPublisher implements AuctionEventPublisher {

    @Override
    public void publishAuctionEvent(Long auctionId, Map<String, Object> eventData) {
        // no-op
    }
}
