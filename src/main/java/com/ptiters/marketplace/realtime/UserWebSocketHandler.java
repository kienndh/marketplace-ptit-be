package com.ptiters.marketplace.realtime;

import com.ptiters.marketplace.user.CurrentUserResolver;
import com.ptiters.marketplace.user.User;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket handler cho kênh cá nhân {@code /ws/me}, khớp {@code app/modules/orders/websocket.py}:
 * <ul>
 *   <li>Xác thực Firebase JWT qua query {@code ?token=} hoặc tin nhắn đầu tiên
 *       {@code {"type":"AUTH","token":"..."}} trong vòng 5 giây.</li>
 *   <li>Từ chối/đóng kết nối bằng mã {@code 1008} ({@link CloseStatus#POLICY_VIOLATION}) khi token sai,
 *       hết hạn, timeout hoặc user bị {@code BANNED}/{@code INACTIVE}.</li>
 *   <li>Hỗ trợ heartbeat {@code "ping"}, {@code "heartbeat"} → {@code "pong"} và
 *       {@code {"type":"PING"|"HEARTBEAT"}} → {@code {"type":"PONG"}} kèm cập nhật presence.</li>
 * </ul>
 */
@Component
public class UserWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(UserWebSocketHandler.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final UserConnectionManager userConnectionManager;
    private final PresenceService presenceService;
    private final JwtDecoder jwtDecoder;
    private final CurrentUserResolver currentUserResolver;
    private final ClientIpResolver clientIpResolver;
    private final JsonMapper jsonMapper;
    private final long authTimeoutMs;

    private final ConcurrentHashMap<String, SessionAuthState> sessionStates = new ConcurrentHashMap<>();
    private final ScheduledExecutorService authTimeoutScheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "ws-auth-timeout");
        t.setDaemon(true);
        return t;
    });

    public UserWebSocketHandler(
            UserConnectionManager userConnectionManager,
            PresenceService presenceService,
            JwtDecoder jwtDecoder,
            CurrentUserResolver currentUserResolver,
            ClientIpResolver clientIpResolver,
            ObjectProvider<JsonMapper> jsonMapperProvider,
            @Value("${marketplace.ws.auth-timeout-ms:5000}") long authTimeoutMs) {
        this.userConnectionManager = userConnectionManager;
        this.presenceService = presenceService;
        this.jwtDecoder = jwtDecoder;
        this.currentUserResolver = currentUserResolver;
        this.clientIpResolver = clientIpResolver;
        this.jsonMapper = jsonMapperProvider.getIfAvailable(() -> JsonMapper.builder().build());
        this.authTimeoutMs = Math.max(50L, authTimeoutMs);
    }

    @PreDestroy
    void shutdownScheduler() {
        authTimeoutScheduler.shutdownNow();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        SessionAuthState state = new SessionAuthState();
        sessionStates.put(session.getId(), state);

        String queryToken = extractQueryToken(session.getUri());
        if (queryToken != null && !queryToken.isBlank()) {
            synchronized (state) {
                if (state.closed) {
                    return;
                }
                if (!authenticateAndRegister(session, state, queryToken)) {
                    closePolicyViolation(session, state);
                }
            }
            return;
        }

        // Không có ?token=: chờ thông điệp {"type":"AUTH","token":...} trong authTimeoutMs (mặc định 5s)
        ScheduledFuture<?> future = authTimeoutScheduler.schedule(() -> {
            synchronized (state) {
                if (state.userId == null && !state.closed) {
                    log.debug("Private user socket auth timed out for session {} (ip={})",
                            session.getId(), ClientIpResolver.maskIp(clientIpResolver.resolveClientIp(session)));
                    closePolicyViolation(session, state);
                }
            }
        }, authTimeoutMs, TimeUnit.MILLISECONDS);

        synchronized (state) {
            if (state.closed || state.userId != null) {
                future.cancel(false);
            } else {
                state.authTimeoutFuture = future;
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        SessionAuthState state = sessionStates.get(session.getId());
        if (state == null) {
            return;
        }

        String payload = message.getPayload();
        Long authenticatedUserId;

        synchronized (state) {
            if (state.closed) {
                return;
            }
            if (state.userId == null) {
                cancelTimeout(state);
                String token = extractAuthMessageToken(payload);
                if (token == null || token.isBlank() || !authenticateAndRegister(session, state, token)) {
                    closePolicyViolation(session, state);
                }
                return;
            }
            authenticatedUserId = state.userId;
        }

        handleAuthenticatedHeartbeat(session, authenticatedUserId, payload);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Transport error on private user socket {}: {}",
                session.getId(), exception != null ? exception.getMessage() : "unknown");
        cleanupSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        cleanupSession(session);
    }

    private boolean authenticateAndRegister(WebSocketSession session, SessionAuthState state, String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token.trim());
            User user = currentUserResolver.resolveActiveFromJwt(jwt);
            if (user == null || user.getUserId() == null || state.closed || !session.isOpen()) {
                return false;
            }
            state.userId = user.getUserId();
            userConnectionManager.connect(user.getUserId(), session);
            return true;
        } catch (Exception e) {
            log.warn("Private user socket auth failed (ip={}): {}",
                    ClientIpResolver.maskIp(clientIpResolver.resolveClientIp(session)), e.getMessage());
            return false;
        }
    }

    private void handleAuthenticatedHeartbeat(WebSocketSession session, Long userId, String data) {
        if ("ping".equals(data) || "heartbeat".equals(data)) {
            presenceService.recordUserPresence(userId);
            userConnectionManager.sendTextToSession(session, "pong");
            return;
        }
        try {
            Map<String, Object> msg = jsonMapper.readValue(data, MAP_TYPE);
            Object type = msg != null ? msg.get("type") : null;
            if ("PING".equals(type) || "HEARTBEAT".equals(type)) {
                presenceService.recordUserPresence(userId);
                userConnectionManager.sendTextToSession(session, "{\"type\":\"PONG\"}");
            }
        } catch (Exception ignored) {
            // Bỏ qua frame text không phải heartbeat hợp lệ
        }
    }

    private String extractAuthMessageToken(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> msg = jsonMapper.readValue(rawJson, MAP_TYPE);
            if (msg == null || !"AUTH".equals(msg.get("type"))) {
                return null;
            }
            Object tokenObj = msg.get("token");
            return tokenObj instanceof String s && !s.isBlank() ? s : null;
        } catch (Exception e) {
            return null;
        }
    }

    static String extractQueryToken(URI uri) {
        if (uri == null) {
            return null;
        }
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            if ("token".equals(key)) {
                String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                return value.isBlank() ? null : value;
            }
        }
        return null;
    }

    private void closePolicyViolation(WebSocketSession session, SessionAuthState state) {
        cancelTimeout(state);
        state.closed = true;
        sessionStates.remove(session.getId());
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.POLICY_VIOLATION);
            }
        } catch (Exception ignored) {
            // no-op
        }
    }

    private void cleanupSession(WebSocketSession session) {
        SessionAuthState state = sessionStates.remove(session.getId());
        if (state == null) {
            return;
        }
        Long connectedUserId;
        synchronized (state) {
            cancelTimeout(state);
            state.closed = true;
            connectedUserId = state.userId;
        }
        if (connectedUserId != null) {
            userConnectionManager.disconnect(connectedUserId, session);
        }
    }

    private static void cancelTimeout(SessionAuthState state) {
        if (state.authTimeoutFuture != null) {
            state.authTimeoutFuture.cancel(false);
            state.authTimeoutFuture = null;
        }
    }

    private static final class SessionAuthState {
        Long userId;
        ScheduledFuture<?> authTimeoutFuture;
        boolean closed;
    }
}
