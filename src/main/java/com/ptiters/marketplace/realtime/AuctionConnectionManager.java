package com.ptiters.marketplace.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
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
 * Quản lý kết nối WebSocket theo từng phòng đấu giá ({@code /ws/auctions/{auctionId}}),
 * khớp {@code AuctionConnectionManager} trong {@code app/modules/auctions/websocket.py}.
 */
@Component
public class AuctionConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(AuctionConnectionManager.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 64 * 1024;

    private final RedisPubSubBridge redisBridge;
    private final JsonMapper jsonMapper;

    private final ConcurrentHashMap<Long, Set<WebSocketSession>> connectionsByAuction = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WebSocketSession> decoratedSessionsById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Object> auctionLocks = new ConcurrentHashMap<>();

    public AuctionConnectionManager(
            ObjectProvider<RedisPubSubBridge> redisBridgeProvider,
            ObjectProvider<JsonMapper> jsonMapperProvider) {
        this.redisBridge = redisBridgeProvider.getIfAvailable();
        this.jsonMapper = jsonMapperProvider.getIfAvailable(() -> JsonMapper.builder().build());
    }

    public void connect(Long auctionId, WebSocketSession rawSession) {
        if (auctionId == null || rawSession == null) {
            return;
        }
        WebSocketSession decorated = decorate(rawSession);
        int activeCount;

        synchronized (lockFor(auctionId)) {
            Set<WebSocketSession> sessions = connectionsByAuction.computeIfAbsent(
                    auctionId, k -> ConcurrentHashMap.newKeySet());
            boolean wasEmpty = sessions.isEmpty();
            sessions.add(decorated);
            decoratedSessionsById.put(rawSession.getId(), decorated);
            activeCount = sessions.size();

            if (wasEmpty && redisBridge != null) {
                redisBridge.subscribeAuction(auctionId, payload -> broadcastLocal(auctionId, payload));
            }
        }

        log.info("WebSocket client connected to auction {}. Active sockets: {}", auctionId, activeCount);
    }

    public void disconnect(Long auctionId, WebSocketSession rawSession) {
        if (auctionId == null || rawSession == null) {
            return;
        }
        WebSocketSession target = decoratedSessionsById.remove(rawSession.getId());
        if (target == null) {
            target = rawSession;
        }
        boolean becameEmpty = false;
        synchronized (lockFor(auctionId)) {
            Set<WebSocketSession> sessions = connectionsByAuction.get(auctionId);
            if (sessions != null) {
                sessions.remove(target);
                if (sessions.isEmpty()) {
                    connectionsByAuction.remove(auctionId);
                    becameEmpty = true;
                    if (redisBridge != null) {
                        redisBridge.unsubscribeAuction(auctionId);
                    }
                }
            }
        }
        if (becameEmpty) {
            auctionLocks.remove(auctionId);
        }
        log.info("WebSocket client disconnected from auction {}", auctionId);
    }

    public void broadcastLocal(Long auctionId, Map<String, Object> message) {
        if (auctionId == null || message == null) {
            return;
        }
        Set<WebSocketSession> sessionSet = connectionsByAuction.get(auctionId);
        if (sessionSet == null || sessionSet.isEmpty()) {
            return;
        }

        String jsonPayload;
        try {
            jsonPayload = jsonMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Failed to serialize auction event for auction {}: {}", auctionId, e.getMessage());
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
                log.debug("Failed to send to client on auction {}: {}", auctionId, e.getMessage());
                deadSessions.add(ws);
            }
        }

        if (!deadSessions.isEmpty()) {
            synchronized (lockFor(auctionId)) {
                Set<WebSocketSession> sessions = connectionsByAuction.get(auctionId);
                if (sessions != null) {
                    for (WebSocketSession dead : deadSessions) {
                        sessions.remove(dead);
                        decoratedSessionsById.remove(dead.getId());
                    }
                    if (sessions.isEmpty()) {
                        connectionsByAuction.remove(auctionId);
                        if (redisBridge != null) {
                            redisBridge.unsubscribeAuction(auctionId);
                        }
                        auctionLocks.remove(auctionId);
                    }
                }
            }
        }
    }

    public int activeSessionCount(Long auctionId) {
        if (auctionId == null) {
            return 0;
        }
        Set<WebSocketSession> sessions = connectionsByAuction.get(auctionId);
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
            log.debug("Failed to send text frame to auction session {}: {}", rawSession.getId(), e.getMessage());
        }
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

    private Object lockFor(Long auctionId) {
        return auctionLocks.computeIfAbsent(auctionId, k -> new Object());
    }
}
