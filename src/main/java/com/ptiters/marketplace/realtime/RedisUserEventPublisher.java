package com.ptiters.marketplace.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Triển khai {@link UserEventPublisher} giao tới WebSocket cục bộ và fan-out qua Redis Pub/Sub
 * kênh {@code user:{id}:events}, khớp {@code app/modules/orders/websocket.py}.
 */
@Service
@ConditionalOnExpression(
        "!'${REDIS_HOST:}'.isBlank() || !'${REDIS_URL:}'.isBlank() "
        + "|| !'${spring.data.redis.host:}'.isBlank() || !'${spring.data.redis.url:}'.isBlank()"
)
public class RedisUserEventPublisher implements UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisUserEventPublisher.class);

    private final UserConnectionManager userConnectionManager;
    private final RedisPubSubBridge redisPubSubBridge;

    public RedisUserEventPublisher(
            UserConnectionManager userConnectionManager,
            RedisPubSubBridge redisPubSubBridge) {
        this.userConnectionManager = userConnectionManager;
        this.redisPubSubBridge = redisPubSubBridge;
    }

    @Override
    public void publishUserEvent(Long userId, Map<String, Object> eventData) {
        if (userId == null || eventData == null) {
            return;
        }
        try {
            userConnectionManager.sendToUserLocal(userId, eventData);
        } catch (Exception e) {
            log.error("Failed local delivery for user {}: {}", userId, e.getMessage());
        }
        redisPubSubBridge.publish(RedisPubSubBridge.userChannel(userId), eventData);
    }

    @Override
    public void revokeUserSessions(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            userConnectionManager.revokeUserConnections(userId);
        } catch (Exception e) {
            log.error("Failed local socket revocation for user {}: {}", userId, e.getMessage());
        }
        redisPubSubBridge.publish(RedisPubSubBridge.userChannel(userId), Map.of("type", "_DISCONNECT"));
    }
}
