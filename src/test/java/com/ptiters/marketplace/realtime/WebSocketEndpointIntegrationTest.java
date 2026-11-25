package com.ptiters.marketplace.realtime;

import com.ptiters.marketplace.auction.Auction;
import com.ptiters.marketplace.auction.AuctionRepository;
import com.ptiters.marketplace.auction.AuctionStatus;
import com.ptiters.marketplace.category.Category;
import com.ptiters.marketplace.category.CategoryRepository;
import com.ptiters.marketplace.product.Product;
import com.ptiters.marketplace.product.ProductApprovalStatus;
import com.ptiters.marketplace.product.ProductRepository;
import com.ptiters.marketplace.product.ProductStatus;
import com.ptiters.marketplace.product.ProductTestFixtures;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserAuthIdentityRepository;
import com.ptiters.marketplace.user.UserRepository;
import com.ptiters.marketplace.user.UserRole;
import com.ptiters.marketplace.user.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"marketplace.ws.auth-timeout-ms=300"}
)
@ActiveProfiles("test")
class WebSocketEndpointIntegrationTest {

    @TestConfiguration
    static class WsTestSecurityConfig {
        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            return ProductTestFixtures.createTestJwtDecoder();
        }
    }

    static class TestWsClient extends TextWebSocketHandler {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.offer(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closed.complete(status);
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuthIdentityRepository authIdentityRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private UserConnectionManager userConnectionManager;

    @Autowired
    private AuctionConnectionManager auctionConnectionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    private final List<Long> createdAuctionIds = new ArrayList<>();
    private final List<Long> createdProductIds = new ArrayList<>();
    private final List<Integer> createdCategoryIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @BeforeEach
    void safetyBoundary() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT current_database()")) {
            assertTrue(rs.next());
            assertTrue(rs.getString(1).matches("marketplace_java_test(_[a-z0-9]+)?"),
                    "Safety violation: test must run on marketplace_java_test*");
        }
    }

    @AfterEach
    void cleanUp() {
        for (Long auctionId : createdAuctionIds) {
            jdbc.update("DELETE FROM auctions WHERE auction_id = ?", auctionId);
        }
        for (Long productId : createdProductIds) {
            jdbc.update("DELETE FROM products WHERE product_id = ?", productId);
        }
        for (Integer categoryId : createdCategoryIds) {
            jdbc.update("DELETE FROM categories WHERE category_id = ?", categoryId);
        }
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM user_auth_identities WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
        }
        createdAuctionIds.clear();
        createdProductIds.clear();
        createdCategoryIds.clear();
        createdUserIds.clear();

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM auctions", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM products", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM users", Integer.class));
    }

    private User createActiveUser(String prefix) {
        String uid = prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
        User user = ProductTestFixtures.createUser(userRepository, uid + "@synthetic.local", "WS User", UserRole.STUDENT);
        ProductTestFixtures.createAuthIdentity(authIdentityRepository, user, uid);
        createdUserIds.add(user.getUserId());
        return user;
    }

    private String tokenForUser(User user) {
        String uid = authIdentityRepository.findAll().stream()
                .filter(id -> id.getUser().getUserId().equals(user.getUserId()))
                .findFirst().orElseThrow()
                .getProviderUid();
        return ProductTestFixtures.createToken(uid, user.getEmail());
    }

    private Auction createAuction(AuctionStatus status) {
        User seller = createActiveUser("ws_seller");
        Category cat = ProductTestFixtures.createCategory(categoryRepository, "wscat");
        createdCategoryIds.add(cat.getCategoryId());
        Product prod = ProductTestFixtures.createProduct(
                productRepository, seller, cat, "Auction Item",
                new BigDecimal("100000"), ProductStatus.AVAILABLE, ProductApprovalStatus.APPROVED);
        createdProductIds.add(prod.getProductId());

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Auction auction = Auction.builder()
                .productId(prod.getProductId())
                .startingPrice(new BigDecimal("100000"))
                .bidStep(new BigDecimal("10000"))
                .startsAt(status == AuctionStatus.SCHEDULED ? now.plusHours(1) : now.minusHours(1))
                .endsAt(now.plusHours(2))
                .status(status)
                .build();
        Auction saved = auctionRepository.saveAndFlush(auction);
        createdAuctionIds.add(saved.getAuctionId());
        return saved;
    }

    @Test
    @DisplayName("/ws/me: xác thực qua ?token= hợp lệ, heartbeat ping/PING, từ chối token sai và BANNED/INACTIVE bằng 1008")
    void userWsQueryTokenAuthAndStatusEnforcement() throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();

        // 1. Valid active user with ?token=
        User active = createActiveUser("ws_q");
        String validToken = tokenForUser(active);
        TestWsClient validHandler = new TestWsClient();
        WebSocketSession s1 = client.execute(
                validHandler, null, URI.create("ws://localhost:" + port + "/ws/me?token=" + validToken))
                .get(5, TimeUnit.SECONDS);

        s1.sendMessage(new TextMessage("ping"));
        assertEquals("pong", validHandler.messages.poll(3, TimeUnit.SECONDS));

        s1.sendMessage(new TextMessage("{\"type\":\"HEARTBEAT\"}"));
        assertEquals("{\"type\":\"PONG\"}", validHandler.messages.poll(3, TimeUnit.SECONDS));
        s1.close();

        // 2. Invalid JWT -> closed with 1008
        TestWsClient invalidHandler = new TestWsClient();
        client.execute(invalidHandler, null, URI.create("ws://localhost:" + port + "/ws/me?token=not-a-jwt"))
                .get(5, TimeUnit.SECONDS);
        CloseStatus invalidClose = invalidHandler.closed.get(3, TimeUnit.SECONDS);
        assertEquals(1008, invalidClose.getCode());

        // 3. BANNED user -> closed with 1008
        User banned = createActiveUser("ws_ban");
        String bannedToken = tokenForUser(banned);
        banned.setStatus(UserStatus.BANNED);
        userRepository.saveAndFlush(banned);

        TestWsClient bannedHandler = new TestWsClient();
        client.execute(bannedHandler, null, URI.create("ws://localhost:" + port + "/ws/me?token=" + bannedToken))
                .get(5, TimeUnit.SECONDS);
        assertEquals(1008, bannedHandler.closed.get(3, TimeUnit.SECONDS).getCode());

        // 4. INACTIVE user -> closed with 1008
        User inactive = createActiveUser("ws_inact");
        String inactiveToken = tokenForUser(inactive);
        inactive.setStatus(UserStatus.INACTIVE);
        userRepository.saveAndFlush(inactive);

        TestWsClient inactiveHandler = new TestWsClient();
        client.execute(inactiveHandler, null, URI.create("ws://localhost:" + port + "/ws/me?token=" + inactiveToken))
                .get(5, TimeUnit.SECONDS);
        assertEquals(1008, inactiveHandler.closed.get(3, TimeUnit.SECONDS).getCode());
    }

    @Test
    @DisplayName("/ws/me: xác thực in-band AUTH, đa thiết bị nhận event, revoke đóng tất cả thiết bị với 1008, sai/timeout đóng 1008")
    void userWsInBandAuthMultiDeviceAndRevoke() throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();

        // 1. Timeout when no AUTH sent within configured auth-timeout-ms (300ms in test)
        TestWsClient timeoutHandler = new TestWsClient();
        client.execute(timeoutHandler, null, URI.create("ws://localhost:" + port + "/ws/me"))
                .get(5, TimeUnit.SECONDS);
        assertEquals(1008, timeoutHandler.closed.get(3, TimeUnit.SECONDS).getCode());

        // 2. Malformed AUTH message -> closed with 1008
        TestWsClient badAuthHandler = new TestWsClient();
        WebSocketSession badSession = client.execute(
                badAuthHandler, null, URI.create("ws://localhost:" + port + "/ws/me"))
                .get(5, TimeUnit.SECONDS);
        badSession.sendMessage(new TextMessage("{\"type\":\"NOT_AUTH\"}"));
        assertEquals(1008, badAuthHandler.closed.get(3, TimeUnit.SECONDS).getCode());

        // 3. Valid in-band AUTH on 2 concurrent devices for same user
        User user = createActiveUser("ws_multi");
        String token = tokenForUser(user);

        TestWsClient h1 = new TestWsClient();
        TestWsClient h2 = new TestWsClient();
        WebSocketSession ws1 = client.execute(h1, null, URI.create("ws://localhost:" + port + "/ws/me"))
                .get(5, TimeUnit.SECONDS);
        WebSocketSession ws2 = client.execute(h2, null, URI.create("ws://localhost:" + port + "/ws/me"))
                .get(5, TimeUnit.SECONDS);

        ws1.sendMessage(new TextMessage("{\"type\":\"AUTH\",\"token\":\"" + token + "\"}"));
        ws2.sendMessage(new TextMessage("{\"type\":\"AUTH\",\"token\":\"" + token + "\"}"));

        // Wait until both sessions confirm auth via ping -> pong
        ws1.sendMessage(new TextMessage("ping"));
        ws2.sendMessage(new TextMessage("ping"));
        assertEquals("pong", h1.messages.poll(3, TimeUnit.SECONDS));
        assertEquals("pong", h2.messages.poll(3, TimeUnit.SECONDS));
        assertEquals(2, userConnectionManager.activeSessionCount(user.getUserId()));

        // Send event to local connections -> both devices receive JSON
        userConnectionManager.sendToUserLocal(user.getUserId(), Map.of("type", "ORDER_CREATED", "order_id", 55));
        String msg1 = h1.messages.poll(3, TimeUnit.SECONDS);
        String msg2 = h2.messages.poll(3, TimeUnit.SECONDS);
        assertNotNull(msg1);
        assertNotNull(msg2);
        assertTrue(msg1.contains("\"ORDER_CREATED\"") && msg1.contains("55"));
        assertTrue(msg2.contains("\"ORDER_CREATED\"") && msg2.contains("55"));

        // Revoke sessions -> both sockets close with 1008
        userConnectionManager.revokeUserConnections(user.getUserId());
        assertEquals(1008, h1.closed.get(3, TimeUnit.SECONDS).getCode());
        assertEquals(1008, h2.closed.get(3, TimeUnit.SECONDS).getCode());
        assertEquals(0, userConnectionManager.activeSessionCount(user.getUserId()));
    }

    @Test
    @DisplayName("/ws/auctions/{id}: cho phép ACTIVE/SCHEDULED + broadcast, từ chối ENDED/CANCELLED/không hợp lệ bằng 1008")
    void auctionRoomEndpointLifecycleAndHardening() throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();

        Auction active = createAuction(AuctionStatus.ACTIVE);
        Auction scheduled = createAuction(AuctionStatus.SCHEDULED);
        Auction ended = createAuction(AuctionStatus.ENDED);
        Auction cancelled = createAuction(AuctionStatus.CANCELLED);

        // ACTIVE room connects, responds ping -> pong, and receives broadcast
        TestWsClient activeHandler = new TestWsClient();
        WebSocketSession sActive = client.execute(
                activeHandler, null, URI.create("ws://localhost:" + port + "/ws/auctions/" + active.getAuctionId()))
                .get(5, TimeUnit.SECONDS);
        sActive.sendMessage(new TextMessage("ping"));
        assertEquals("pong", activeHandler.messages.poll(3, TimeUnit.SECONDS));

        auctionConnectionManager.broadcastLocal(active.getAuctionId(), Map.of(
                "event", "BID_PLACED",
                "auction_id", active.getAuctionId(),
                "current_price", "110000"
        ));
        String bidMsg = activeHandler.messages.poll(3, TimeUnit.SECONDS);
        assertNotNull(bidMsg);
        assertTrue(bidMsg.contains("\"BID_PLACED\""));
        sActive.close();

        // SCHEDULED room connects cleanly
        TestWsClient schedHandler = new TestWsClient();
        WebSocketSession sSched = client.execute(
                schedHandler, null, URI.create("ws://localhost:" + port + "/ws/auctions/" + scheduled.getAuctionId()))
                .get(5, TimeUnit.SECONDS);
        sSched.sendMessage(new TextMessage("ping"));
        assertEquals("pong", schedHandler.messages.poll(3, TimeUnit.SECONDS));
        sSched.close();

        // ENDED, CANCELLED, non-existent, invalid syntax -> closed with 1008 and never registered
        for (String badSuffix : List.of(
                String.valueOf(ended.getAuctionId()),
                String.valueOf(cancelled.getAuctionId()),
                "99999999",
                "0",
                "-5",
                "abc",
                "1/extra")) {
            TestWsClient rejectHandler = new TestWsClient();
            client.execute(rejectHandler, null, URI.create("ws://localhost:" + port + "/ws/auctions/" + badSuffix))
                    .get(5, TimeUnit.SECONDS);
            assertEquals(1008, rejectHandler.closed.get(3, TimeUnit.SECONDS).getCode(),
                    "Expected 1008 close for /ws/auctions/" + badSuffix);
        }

        assertEquals(0, auctionConnectionManager.activeSessionCount(ended.getAuctionId()));
        assertEquals(0, auctionConnectionManager.activeSessionCount(cancelled.getAuctionId()));
    }
}
