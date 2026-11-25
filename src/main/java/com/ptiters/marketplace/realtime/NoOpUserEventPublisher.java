package com.ptiters.marketplace.realtime;

import java.util.Map;

/** Bản mặc định an toàn: không có kết nối WebSocket/Redis. */
public class NoOpUserEventPublisher implements UserEventPublisher {

    @Override
    public void publishUserEvent(Long userId, Map<String, Object> eventData) {
        // no-op
    }

    @Override
    public void revokeUserSessions(Long userId) {
        // no-op
    }
}
