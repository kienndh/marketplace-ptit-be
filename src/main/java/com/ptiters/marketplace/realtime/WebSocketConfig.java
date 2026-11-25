package com.ptiters.marketplace.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Đăng ký các WebSocket endpoints realtime ({@code /ws/me} và {@code /ws/auctions/**}).
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final UserWebSocketHandler userWebSocketHandler;
    private final AuctionRoomWebSocketHandler auctionRoomWebSocketHandler;

    public WebSocketConfig(
            UserWebSocketHandler userWebSocketHandler,
            AuctionRoomWebSocketHandler auctionRoomWebSocketHandler) {
        this.userWebSocketHandler = userWebSocketHandler;
        this.auctionRoomWebSocketHandler = auctionRoomWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(userWebSocketHandler, "/ws/me")
                .setAllowedOriginPatterns("*");
        registry.addHandler(auctionRoomWebSocketHandler, "/ws/auctions/**")
                .setAllowedOriginPatterns("*");
    }
}
