package com.ptiters.marketplace.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý kết nối WebSocket đa thiết bị cho từng người dùng ({@code /ws/me}),
 * khớp {@code UserConnectionManager} trong {@code app/modules/orders/websocket.py}.
 */
@Component
public class UserConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(UserConnectionManager.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 64 * 1024;

    private final PresenceService presenceService;
    private final RedisPubSubBridge redisBridge;
    private final JsonMapper jsonMapper;

    private final ConcurrentHashMap<Long, Set<WebSocketSession>> connectionsByUser = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WebSocketSession> decoratedSessionsById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Object> userLocks = new ConcurrentHashMap<>();

    public UserConnectionManager(
            PresenceService presenceService,
            ObjectProvider<RedisPubSubBridge> redisBridgeProvider,
            ObjectProvider<JsonMapper> jsonMapperProvider) {
        this.presenceService = presenceService;
        this.redisBridge = redisBridgeProvider.getIfAvailable();
        this.jsonMapper = jsonMapperProvider.getIfAvailable(() -> JsonMapper.builder().build());
    }

    public void connect(Long userId, WebSocketSession rawSession) {
        if (userId == null || rawSession == null) {
            return;
        }
        WebSocketSession decorated = decorate(rawSession);
        boolean firstLocalSocket;
        int activeCount;

        synchronized (lockFor(userId)) {
            Set<WebSocketSession> sessions = connectionsByUser.computeIfAbsent(
                    userId, k -> ConcurrentHashMap.newKeySet());
            boolean wasEmpty = sessions.isEmpty();
            sessions.add(decorated);
            decoratedSessionsById.put(rawSession.getId(), decorated);
            firstLocalSocket = wasEmpty;
            activeCount = sessions.size();

            if (firstLocalSocket && redisBridge != null) {
                redisBridge.subscribeUser(userId, payload -> handleRemoteUserPayload(userId, payload));
            }
        }

        presenceService.recordUserPresence(userId);
        log.info("Private user socket connected for user_id={}. Active sockets: {}", userId, activeCount);
    }

    public void disconnect(Long userId, WebSocketSession rawSession) {
        if (userId == null || rawSession == null) {
            return;
        }
        WebSocketSession target = decoratedSessionsById.remove(rawSession.getId());
        if (target == null) {
            target = rawSession;
        }
        boolean becameEmpty = false;
        synchronized (lockFor(userId)) {
            Set<WebSocketSession> sessions = connectionsByUser.get(userId);
            if (sessions != null) {
                sessions.remove(target);
                if (sessions.isEmpty()) {
                    connectionsByUser.remove(userId);
                    becameEmpty = true;
                    if (redisBridge != null) {
                        redisBridge.unsubscribeUser(userId);
                    }
                }
            }
        }
        if (becameEmpty) {
            userLocks.remove(userId);
        }
        log.info("Private user socket disconnected for user_id={}", userId);
    }

    public void revokeUserConnections(Long userId) {
        revokeUserConnections(userId, CloseStatus.POLICY_VIOLATION);
    }

    public void revokeUserConnections(Long userId, CloseStatus closeStatus) {
        if (userId == null) {
            return;
        }
        List<WebSocketSession> toClose;
        synchronized (lockFor(userId)) {
            Set<WebSocketSession> removed = connectionsByUser.remove(userId);
            toClose = removed == null ? List.of() : new ArrayList<>(removed);
            for (WebSocketSession session : toClose) {
                decoratedSessionsById.remove(session.getId());
            }
            if (redisBridge != null) {
                redisBridge.unsubscribeUser(userId);
            }
        }
        userLocks.remove(userId);

        for (WebSocketSession ws : toClose) {
            try {
                if (ws.isOpen()) {
                    ws.close(closeStatus);
                }
            } catch (Exception e) {
                log.debug("Error closing revoked socket for user {}: {}", userId, e.getMessage());
            }
        }
        log.info("Revoked all active sockets for user_id={}", userId);
    }

    public void sendToUserLocal(Long userId, Map<String, Object> message) {
        if (userId == null || message == null) {
            return;
        }
        Set<WebSocketSession> sessionSet = connectionsByUser.get(userId);
        if (sessionSet == null || sessionSet.isEmpty()) {
            return;
        }

        String jsonPayload;
        try {
            jsonPayload = jsonMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Failed to serialize private event for user {}: {}", userId, e.getMessage());
            return;
        }

        List<WebSocketSession> snapshot = new ArrayList<>(sessionSet);
        List<WebSocketSession> deadSessions = new ArrayList<>();

        for (WebSocketSession ws : snapshot) {
            if (!ws.isOpen()) {
                deadSessions.add(ws);
                continue;
            }
            try {
                ws.sendMessage(new TextMessage(jsonPayload));
            } catch (IOException | RuntimeException e) {
                log.debug("Failed to send private event to user {}: {}", userId, e.getMessage());
                deadSessions.add(ws);
            }
        }

        if (!deadSessions.isEmpty()) {
            synchronized (lockFor(userId)) {
                Set<WebSocketSession> sessions = connectionsByUser.get(userId);
                if (sessions != null) {
                    for (WebSocketSession dead : deadSessions) {
                        sessions.remove(dead);
                        decoratedSessionsById.remove(dead.getId());
                    }
                    if (sessions.isEmpty()) {
                        connectionsByUser.remove(userId);
                        if (redisBridge != null) {
                            redisBridge.unsubscribeUser(userId);
                        }
                        userLocks.remove(userId);
                    }
                }
            }
        }
    }

    public int activeSessionCount(Long userId) {
        if (userId == null) {
            return 0;
        }
        Set<WebSocketSession> sessions = connectionsByUser.get(userId);
        return sessions == null ? 0 : sessions.size();
    }

    public void sendTextToSession(WebSocketSession rawSession, String text) {
        if (rawSession == null || text == null) {
            return;
        }
        WebSocketSession target = decoratedSessionsById.getOrDefault(rawSession.getId(), rawSession);
        if (!target.isOpen()) {
            return;
        }
        try {
            target.sendMessage(new TextMessage(text));
        } catch (IOException | RuntimeException e) {
            log.debug("Failed to send text frame to session {}: {}", rawSession.getId(), e.getMessage());
        }
    }

    void handleRemoteUserPayload(Long userId, Map<String, Object> payload) {
        if (payload == null) {
            return;
        }
        if ("_DISCONNECT".equals(payload.get("type"))) {
            revokeUserConnections(userId, CloseStatus.POLICY_VIOLATION);
            return;
        }
        sendToUserLocal(userId, payload);
    }

    private WebSocketSession decorate(WebSocketSession rawSession) {
        WebSocketSession existing = decoratedSessionsById.get(rawSession.getId());
        if (existing != null) {
            return existing;
        }
        if (rawSession instanceof ConcurrentWebSocketSessionDecorator) {
            return rawSession;
        }
        return new ConcurrentWebSocketSessionDecorator(rawSession, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES);
    }

    private Object lockFor(Long userId) {
        return userLocks.computeIfAbsent(userId, k -> new Object());
    }
}
