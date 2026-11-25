package com.ptiters.marketplace.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Cầu nối Redis Pub/Sub cho fan-out đa instance trên kênh {@code user:{id}:events} và
 * {@code auction:{id}:events}, sử dụng transport envelope {@code {"_meta":{"origin":...},"payload":...}}
 * để chống lặp sự kiện loopback giống Python.
 */
@Component
@ConditionalOnExpression(
        "!'${REDIS_HOST:}'.isBlank() || !'${REDIS_URL:}'.isBlank() "
        + "|| !'${spring.data.redis.host:}'.isBlank() || !'${spring.data.redis.url:}'.isBlank()"
)
public class RedisPubSubBridge {

    private static final Logger log = LoggerFactory.getLogger(RedisPubSubBridge.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final String instanceId = UUID.randomUUID().toString();
    private final StringRedisTemplate redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final JsonMapper jsonMapper;

    private final ConcurrentHashMap<Long, MessageListener> userListeners = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, MessageListener> auctionListeners = new ConcurrentHashMap<>();

    public RedisPubSubBridge(
            StringRedisTemplate redisTemplate,
            RedisMessageListenerContainer listenerContainer,
            ObjectProvider<JsonMapper> jsonMapperProvider) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
        this.jsonMapper = jsonMapperProvider.getIfAvailable(() -> JsonMapper.builder().build());
    }

    public String getInstanceId() {
        return instanceId;
    }

    public static String userChannel(Long userId) {
        return "user:" + userId + ":events";
    }

    public static String auctionChannel(Long auctionId) {
        return "auction:" + auctionId + ":events";
    }

    public void publish(String channel, Map<String, Object> payload) {
        if (channel == null || payload == null) {
            return;
        }
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("_meta", Map.of("origin", instanceId));
            envelope.put("payload", payload);
            String json = jsonMapper.writeValueAsString(envelope);
            redisTemplate.convertAndSend(channel, json);
        } catch (Exception e) {
            log.warn("Redis publish failed for channel {}: {}", channel, e.getMessage());
        }
    }

    public void subscribeUser(Long userId, Consumer<Map<String, Object>> consumer) {
        if (userId == null || consumer == null) {
            return;
        }
        userListeners.computeIfAbsent(userId, uid -> {
            String channel = userChannel(uid);
            MessageListener listener = (message, pattern) -> dispatchIncoming(channel, message.getBody(), consumer);
            try {
                listenerContainer.addMessageListener(listener, ChannelTopic.of(channel));
                log.debug("Subscribed to Redis channel {}", channel);
            } catch (Exception e) {
                log.warn("Failed to subscribe to Redis channel {}: {}", channel, e.getMessage());
            }
            return listener;
        });
    }

    public void unsubscribeUser(Long userId) {
        if (userId == null) {
            return;
        }
        MessageListener removed = userListeners.remove(userId);
        if (removed != null) {
            try {
                listenerContainer.removeMessageListener(removed, ChannelTopic.of(userChannel(userId)));
            } catch (Exception e) {
                log.debug("Error unsubscribing user channel {}: {}", userId, e.getMessage());
            }
        }
    }

    public void subscribeAuction(Long auctionId, Consumer<Map<String, Object>> consumer) {
        if (auctionId == null || consumer == null) {
            return;
        }
        auctionListeners.computeIfAbsent(auctionId, aid -> {
            String channel = auctionChannel(aid);
            MessageListener listener = (message, pattern) -> dispatchIncoming(channel, message.getBody(), consumer);
            try {
                listenerContainer.addMessageListener(listener, ChannelTopic.of(channel));
                log.debug("Subscribed to Redis channel {}", channel);
            } catch (Exception e) {
                log.warn("Failed to subscribe to Redis channel {}: {}", channel, e.getMessage());
            }
            return listener;
        });
    }

    public void unsubscribeAuction(Long auctionId) {
        if (auctionId == null) {
            return;
        }
        MessageListener removed = auctionListeners.remove(auctionId);
        if (removed != null) {
            try {
                listenerContainer.removeMessageListener(removed, ChannelTopic.of(auctionChannel(auctionId)));
            } catch (Exception e) {
                log.debug("Error unsubscribing auction channel {}: {}", auctionId, e.getMessage());
            }
        }
    }

    private void dispatchIncoming(String channel, byte[] body, Consumer<Map<String, Object>> consumer) {
        try {
            Map<String, Object> payload = parseIncomingPayload(body);
            if (payload != null) {
                consumer.accept(payload);
            }
        } catch (Exception e) {
            log.error("Error parsing Redis pub/sub message on {}: {}", channel, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> parseIncomingPayload(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        String rawJson = new String(body, StandardCharsets.UTF_8);
        Map<String, Object> rawData = jsonMapper.readValue(rawJson, MAP_TYPE);
        if (rawData == null) {
            return null;
        }
        if (rawData.containsKey("_meta") && rawData.containsKey("payload")) {
            Object metaObj = rawData.get("_meta");
            if (metaObj instanceof Map<?, ?> metaMap) {
                Object origin = metaMap.get("origin");
                if (instanceId.equals(origin)) {
                    // Self-origin: bản sao cục bộ đã được giao trực tiếp, bỏ qua để tránh gửi trùng
                    return null;
                }
            }
            Object inner = rawData.get("payload");
            return (inner instanceof Map<?, ?>) ? (Map<String, Object>) inner : null;
        }
        return rawData;
    }
}
