package com.ptiters.marketplace.realtime;

import com.ptiters.marketplace.auction.AuctionWebSocketHandler;
import com.ptiters.marketplace.order.OrderWebSocketHandler;
import org.springframework.context.annotation.Configuration;

/**
 * Skeleton WebSocket configuration for real-time messaging, order notifications, and auctions.
 * Will implement WebSocketConfigurer once spring-boot-starter-websocket is introduced.
 */
@Configuration
public class WebSocketConfig {

    private final OrderWebSocketHandler orderWebSocketHandler;
    private final AuctionWebSocketHandler auctionWebSocketHandler;

    public WebSocketConfig(OrderWebSocketHandler orderWebSocketHandler, AuctionWebSocketHandler auctionWebSocketHandler) {
        this.orderWebSocketHandler = orderWebSocketHandler;
        this.auctionWebSocketHandler = auctionWebSocketHandler;
    }
}
