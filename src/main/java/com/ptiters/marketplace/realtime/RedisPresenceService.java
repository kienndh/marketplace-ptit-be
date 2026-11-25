package com.ptiters.marketplace.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Triển khai {@link PresenceService} trên Redis, khớp 100% {@code app/core/realtime/presence.py}:
 * key {@code presence:user:<userId>}, TTL 90 giây, lookup hàng loạt bằng {@code MGET}.
 * Mọi lỗi Redis đều trả {@code available=false} hoặc {@code false} — KHÔNG bao giờ ném lỗi.
 */
@Service
@ConditionalOnExpression(
        "!'${REDIS_HOST:}'.isBlank() || !'${REDIS_URL:}'.isBlank() "
        + "|| !'${spring.data.redis.host:}'.isBlank() || !'${spring.data.redis.url:}'.isBlank()"
)
public class RedisPresenceService implements PresenceService {

    private static final Logger log = LoggerFactory.getLogger(RedisPresenceService.class);

    public static final String PRESENCE_KEY_PREFIX = "presence:user:";
    public static final Duration PRESENCE_TTL = Duration.ofSeconds(90);

    private final StringRedisTemplate redisTemplate;

    public RedisPresenceService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean recordUserPresence(Long userId) {
        if (userId == null) {
            return false;
        }
        try {
            redisTemplate.opsForValue().set(presenceKey(userId), "1", PRESENCE_TTL);
            return true;
        } catch (Exception e) {
            log.debug("Redis presence record failed for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    @Override
    public PresenceSnapshot getUsersPresence(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new PresenceSnapshot(true, Set.of());
        }
        List<Long> orderedIds = new ArrayList<>(userIds.size());
        List<String> keys = new ArrayList<>(userIds.size());
        for (Long uid : userIds) {
            if (uid != null) {
                orderedIds.add(uid);
                keys.add(presenceKey(uid));
            }
        }
        if (orderedIds.isEmpty()) {
            return new PresenceSnapshot(true, Set.of());
        }
        try {
            List<String> values = redisTemplate.opsForValue().multiGet(keys);
            Set<Long> onlineIds = new LinkedHashSet<>();
            if (values != null) {
                for (int i = 0; i < orderedIds.size() && i < values.size(); i++) {
                    if (values.get(i) != null) {
                        onlineIds.add(orderedIds.get(i));
                    }
                }
            }
            return new PresenceSnapshot(true, Set.copyOf(onlineIds));
        } catch (Exception e) {
            log.warn("Redis batch presence lookup failed: {}", e.getMessage());
            return PresenceSnapshot.unavailable();
        }
    }

    @Override
    public UserPresence isUserOnline(Long userId) {
        if (userId == null) {
            return new UserPresence(true, false);
        }
        return PresenceService.super.isUserOnline(userId);
    }

    public static String presenceKey(Long userId) {
        return PRESENCE_KEY_PREFIX + userId;
    }
}
