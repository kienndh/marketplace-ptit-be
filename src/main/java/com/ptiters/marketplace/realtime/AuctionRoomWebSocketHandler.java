package com.ptiters.marketplace.realtime;

import com.ptiters.marketplace.auction.Auction;
import com.ptiters.marketplace.auction.AuctionRepository;
import com.ptiters.marketplace.auction.AuctionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler cho phòng đấu giá {@code /ws/auctions/{auctionId}},
 * khớp {@code auction_websocket_endpoint} trong {@code app/modules/auctions/router.py}:
 * <ul>
 *   <li>{@code auction_id} phải là số nguyên dương; phiên đấu giá phải tồn tại và có trạng thái
 *       {@code SCHEDULED} hoặc {@code ACTIVE}.</li>
 *   <li>Vi phạm bất kỳ điều kiện nào → đóng ngay với mã {@code 1008} ({@link CloseStatus#POLICY_VIOLATION})
 *       và không đăng ký vào {@link AuctionConnectionManager}.</li>
 *   <li>Hỗ trợ heartbeat {@code "ping"} → {@code "pong"}.</li>
 * </ul>
 */
@Component
public class AuctionRoomWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AuctionRoomWebSocketHandler.class);
    private static final String AUCTION_WS_PREFIX = "/ws/auctions/";

    private final AuctionConnectionManager auctionConnectionManager;
    private final AuctionRepository auctionRepository;
    private final ClientIpResolver clientIpResolver;

    private final ConcurrentHashMap<String, Long> sessionAuctionIds = new ConcurrentHashMap<>();

    public AuctionRoomWebSocketHandler(
            AuctionConnectionManager auctionConnectionManager,
            AuctionRepository auctionRepository,
            ClientIpResolver clientIpResolver) {
        this.auctionConnectionManager = auctionConnectionManager;
        this.auctionRepository = auctionRepository;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long auctionId = parseAuctionId(session.getUri());
        if (auctionId == null || auctionId <= 0) {
            rejectPolicyViolation(session, "invalid auction_id");
            return;
        }

        Optional<Auction> auctionOpt = auctionRepository.findById(auctionId);
        if (auctionOpt.isEmpty()) {
            rejectPolicyViolation(session, "auction not found");
            return;
        }

        AuctionStatus status = auctionOpt.get().getStatus();
        if (status != AuctionStatus.SCHEDULED && status != AuctionStatus.ACTIVE) {
            rejectPolicyViolation(session, "auction status " + status + " not eligible");
            return;
        }

        sessionAuctionIds.put(session.getId(), auctionId);
        auctionConnectionManager.connect(auctionId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if ("ping".equals(message.getPayload())) {
            auctionConnectionManager.sendTextToSession(session, "pong");
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        cleanupSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        cleanupSession(session);
    }

    static Long parseAuctionId(URI uri) {
        if (uri == null) {
            return null;
        }
        String path = uri.getPath();
        if (path == null || !path.startsWith(AUCTION_WS_PREFIX)) {
            return null;
        }
        String remainder = path.substring(AUCTION_WS_PREFIX.length());
        if (remainder.isEmpty() || remainder.contains("/")) {
            return null;
        }
        try {
            long id = Long.parseLong(remainder);
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void rejectPolicyViolation(WebSocketSession session, String reason) {
        log.debug("Rejecting auction WS connection {} ({}, ip={})",
                session.getId(), reason, ClientIpResolver.maskIp(clientIpResolver.resolveClientIp(session)));
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.POLICY_VIOLATION);
            }
        } catch (Exception ignored) {
            // no-op
        }
    }

    private void cleanupSession(WebSocketSession session) {
        Long auctionId = sessionAuctionIds.remove(session.getId());
        if (auctionId != null) {
            auctionConnectionManager.disconnect(auctionId, session);
        }
    }
}
