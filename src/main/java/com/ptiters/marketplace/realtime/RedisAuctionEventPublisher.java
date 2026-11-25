package com.ptiters.marketplace.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Triển khai {@link AuctionEventPublisher} giao tới WebSocket phòng đấu giá cục bộ và
 * fan-out qua Redis Pub/Sub kênh {@code auction:{id}:events}, khớp {@code app/modules/auctions/websocket.py}.
 */
@Service
@ConditionalOnExpression(
        "!'${REDIS_HOST:}'.isBlank() || !'${REDIS_URL:}'.isBlank() "
        + "|| !'${spring.data.redis.host:}'.isBlank() || !'${spring.data.redis.url:}'.isBlank()"
)
public class RedisAuctionEventPublisher implements AuctionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisAuctionEventPublisher.class);

    private final AuctionConnectionManager auctionConnectionManager;
    private final RedisPubSubBridge redisPubSubBridge;

    public RedisAuctionEventPublisher(
            AuctionConnectionManager auctionConnectionManager,
            RedisPubSubBridge redisPubSubBridge) {
        this.auctionConnectionManager = auctionConnectionManager;
        this.redisPubSubBridge = redisPubSubBridge;
    }

    @Override
    public void publishAuctionEvent(Long auctionId, Map<String, Object> eventData) {
        if (auctionId == null || eventData == null) {
            return;
        }
        try {
            auctionConnectionManager.broadcastLocal(auctionId, eventData);
        } catch (Exception e) {
            log.error("Failed local delivery for auction {}: {}", auctionId, e.getMessage());
        }
        redisPubSubBridge.publish(RedisPubSubBridge.auctionChannel(auctionId), eventData);
    }
}
