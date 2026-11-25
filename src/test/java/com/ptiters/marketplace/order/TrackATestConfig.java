package com.ptiters.marketplace.order;

import com.ptiters.marketplace.product.ProductTestFixtures;
import com.ptiters.marketplace.realtime.AuctionEventPublisher;
import com.ptiters.marketplace.realtime.UserEventPublisher;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Cấu hình test Track A: JwtDecoder ký bằng khoá test + publisher realtime ghi lại sự kiện (kiểm chứng "sau commit"). */
@TestConfiguration
public class TrackATestConfig {

    public static final List<Map<String, Object>> USER_EVENTS = new CopyOnWriteArrayList<>();
    public static final List<Map<String, Object>> AUCTION_EVENTS = new CopyOnWriteArrayList<>();

    @Bean
    @Primary
    public JwtDecoder testJwtDecoder() {
        return ProductTestFixtures.createTestJwtDecoder();
    }

    @Bean
    @Primary
    public UserEventPublisher recordingUserEventPublisher() {
        return new UserEventPublisher() {
            @Override
            public void publishUserEvent(Long userId, Map<String, Object> eventData) {
                java.util.LinkedHashMap<String, Object> copy = new java.util.LinkedHashMap<>(eventData);
                copy.put("_user_id", userId);
                USER_EVENTS.add(copy);
            }

            @Override
            public void revokeUserSessions(Long userId) {
            }
        };
    }

    @Bean
    @Primary
    public AuctionEventPublisher recordingAuctionEventPublisher() {
        return (auctionId, eventData) -> {
            java.util.LinkedHashMap<String, Object> copy = new java.util.LinkedHashMap<>(eventData);
            copy.put("_auction_id", auctionId);
            AUCTION_EVENTS.add(copy);
        };
    }
}
