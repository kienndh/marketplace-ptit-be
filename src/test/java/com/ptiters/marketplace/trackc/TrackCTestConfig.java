package com.ptiters.marketplace.trackc;

import com.ptiters.marketplace.notification.PushNotificationSender;
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

/** Bean test dùng chung Track C: JWT giả lập + publisher ghi nhận để kiểm chứng tác dụng phụ SAU commit. */
@TestConfiguration
public class TrackCTestConfig {

    public static final List<Map<String, Object>> USER_EVENTS = new CopyOnWriteArrayList<>();
    public static final List<Long> REVOKED_USERS = new CopyOnWriteArrayList<>();
    public static final List<Long> CANCELLED_AUCTIONS = new CopyOnWriteArrayList<>();

    public static void reset() {
        USER_EVENTS.clear();
        REVOKED_USERS.clear();
        CANCELLED_AUCTIONS.clear();
    }

    @Bean
    @Primary
    JwtDecoder trackCJwtDecoder() {
        return ProductTestFixtures.createTestJwtDecoder();
    }

    @Bean
    @Primary
    PushNotificationSender trackCPushSender() {
        return (userId, title, body, data) -> { };
    }

    @Bean
    @Primary
    UserEventPublisher trackCUserEventPublisher() {
        return new UserEventPublisher() {
            @Override
            public void publishUserEvent(Long userId, Map<String, Object> eventData) {
                USER_EVENTS.add(Map.of("userId", userId, "event", eventData));
            }

            @Override
            public void revokeUserSessions(Long userId) {
                REVOKED_USERS.add(userId);
            }
        };
    }

    @Bean
    @Primary
    AuctionEventPublisher trackCAuctionEventPublisher() {
        return new AuctionEventPublisher() {
            @Override
            public void publishAuctionEvent(Long auctionId, Map<String, Object> eventData) {
                if ("AUCTION_CANCELLED".equals(eventData.get("event"))) {
                    CANCELLED_AUCTIONS.add(auctionId);
                }
            }
        };
    }
}
