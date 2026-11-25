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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
        properties = {"REDIS_HOST=localhost", "REDIS_PORT=6379"}
)
@ActiveProfiles("test")
class RedisWebSocketEndToEndIntegrationTest {

    @TestConfiguration
    static class RedisWsTestSecurityConfig {
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
    private UserEventPublisher userEventPublisher;

    @Autowired
    private AuctionEventPublisher auctionEventPublisher;

    @Autowired
    private PresenceService presenceService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

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
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    private final List<Long> createdAuctionIds = new ArrayList<>();
    private final List<Long> createdProductIds = new ArrayList<>();
    private final List<Integer> createdCategoryIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<String> redisKeys = new ArrayList<>();

    private static boolean isLocalRedisReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 6379), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    void checkPreconditions() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT current_database()")) {
            assertTrue(rs.next());
            assertTrue(rs.getString(1).matches("marketplace_java_test(_[a-z0-9]+)?"),
                    "Safety violation: test must run on marketplace_java_test*");
        }
        Assumptions.assumeTrue(isLocalRedisReachable(), "Redis sm_redis (localhost:6379) is not reachable");
    }

    @AfterEach
    void cleanUp() {
        if (!redisKeys.isEmpty() && isLocalRedisReachable()) {
            stringRedisTemplate.delete(redisKeys);
        }
        redisKeys.clear();

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

    @Test
    @DisplayName("End-to-end Redis + WebSocket: /ws/me nhận event & revoke 1008, /ws/auctions/{id} nhận BID_PLACED")
    void endToEndRealtimePubSubAndWebSocket() throws Exception {
        String uid = "ws_e2e_" + UUID.randomUUID().toString().substring(0, 8);
        User user = ProductTestFixtures.createUser(userRepository, uid + "@synthetic.local", "WS E2E User", UserRole.STUDENT);
        ProductTestFixtures.createAuthIdentity(authIdentityRepository, user, uid);
        createdUserIds.add(user.getUserId());
        redisKeys.add(RedisPresenceService.presenceKey(user.getUserId()));

        Category cat = ProductTestFixtures.createCategory(categoryRepository, "wse2ecat");
        createdCategoryIds.add(cat.getCategoryId());
        Product prod = ProductTestFixtures.createProduct(
                productRepository, user, cat, "Auction E2E Item",
                new BigDecimal("200000"), ProductStatus.AVAILABLE, ProductApprovalStatus.APPROVED);
        createdProductIds.add(prod.getProductId());

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Auction auction = auctionRepository.saveAndFlush(Auction.builder()
                .productId(prod.getProductId())
                .startingPrice(new BigDecimal("200000"))
                .bidStep(new BigDecimal("20000"))
                .startsAt(now.minusMinutes(10))
                .endsAt(now.plusMinutes(30))
                .status(AuctionStatus.ACTIVE)
                .build());
        createdAuctionIds.add(auction.getAuctionId());

        String token = ProductTestFixtures.createToken(uid, user.getEmail());
        StandardWebSocketClient client = new StandardWebSocketClient();

        // 1. Connect /ws/me with in-band AUTH
        TestWsClient userWsHandler = new TestWsClient();
        WebSocketSession userSession = client.execute(
                userWsHandler, null, URI.create("ws://localhost:" + port + "/ws/me"))
                .get(5, TimeUnit.SECONDS);
        userSession.sendMessage(new TextMessage("{\"type\":\"AUTH\",\"token\":\"" + token + "\"}"));
        userSession.sendMessage(new TextMessage("ping"));
        assertEquals("pong", userWsHandler.messages.poll(3, TimeUnit.SECONDS));

        // Presence recorded in real Redis
        PresenceService.UserPresence presence = presenceService.isUserOnline(user.getUserId());
        assertTrue(presence.available());
        assertTrue(presence.online());

        // 2. Connect /ws/auctions/{id}
        TestWsClient auctionWsHandler = new TestWsClient();
        WebSocketSession auctionSession = client.execute(
                auctionWsHandler, null, URI.create("ws://localhost:" + port + "/ws/auctions/" + auction.getAuctionId()))
                .get(5, TimeUnit.SECONDS);
        auctionSession.sendMessage(new TextMessage("ping"));
        assertEquals("pong", auctionWsHandler.messages.poll(3, TimeUnit.SECONDS));

        // 3. Publish events via UserEventPublisher & AuctionEventPublisher
        userEventPublisher.publishOrderCreated(user.getUserId(), 1234L);
        String userEvent = userWsHandler.messages.poll(3, TimeUnit.SECONDS);
        assertNotNull(userEvent);
        assertTrue(userEvent.contains("\"ORDER_CREATED\"") && userEvent.contains("1234"));

        auctionEventPublisher.publishBidPlaced(
                auction.getAuctionId(), new BigDecimal("220000"), new BigDecimal("20000"), 1L, Instant.now());
        String auctionEvent = auctionWsHandler.messages.poll(3, TimeUnit.SECONDS);
        assertNotNull(auctionEvent);
        assertTrue(auctionEvent.contains("\"BID_PLACED\"") && auctionEvent.contains("\"240000\""));

        // 4. Revoke user sessions -> closes /ws/me with 1008
        userEventPublisher.revokeUserSessions(user.getUserId());
        assertEquals(1008, userWsHandler.closed.get(3, TimeUnit.SECONDS).getCode());

        auctionSession.close();
    }
}
