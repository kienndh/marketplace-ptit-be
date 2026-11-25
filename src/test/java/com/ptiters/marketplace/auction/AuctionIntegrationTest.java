package com.ptiters.marketplace.auction;

import com.jayway.jsonpath.JsonPath;
import com.ptiters.marketplace.category.Category;
import com.ptiters.marketplace.order.TrackATestConfig;
import com.ptiters.marketplace.order.TrackATestSupport;
import com.ptiters.marketplace.product.Product;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.worker.OrderAutoCancelWorker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Đấu giá trên PostgreSQL THẬT: vòng đời, đặt giá tuần tự dưới khoá hàng, đóng phiên, checkout, worker, quyền. */
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=4")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TrackATestConfig.class)
class AuctionIntegrationTest extends TrackATestSupport {

    @Autowired
    private OrderAutoCancelWorker worker;

    private User seller;
    private User alice;
    private User bob;
    private Category category;
    private Product product;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        TrackATestConfig.AUCTION_EVENTS.clear();
        TrackATestConfig.USER_EVENTS.clear();
        seller = newUser("aseller");
        alice = newUser("alice");
        bob = newUser("bob");
        category = newCategory();
        product = newProduct(seller, category, "Đồng hồ cổ", "50000", 1);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
        assertAllTablesEmpty();
    }

    private ResultActions json(User u, String url, String body) throws Exception {
        return mockMvc.perform(auth(post(url), u).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long startAuction() throws Exception {
        MvcResult r = json(seller, "/api/v1/auctions",
                "{\"product_id\":" + product.getProductId() + ",\"bid_step\":20000,\"duration_minutes\":180}")
                .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.auction_id")).longValue();
    }

    private ResultActions bid(User u, long auctionId, long amount) throws Exception {
        return json(u, "/api/v1/auctions/" + auctionId + "/bids", "{\"amount\":" + amount + "}");
    }

    private static final String CHECKOUT = "{\"payment_method\":\"CASH_ON_MEETUP\",\"fulfillment_method\":\"MEETUP\","
            + "\"meetup_place\":\"Cổng trường\",\"meetup_time\":\"2026-12-31T15:00:00Z\","
            + "\"contact_name\":\"Alice\",\"contact_phone\":\"0987654321\"}";

    @Test
    @DisplayName("Quyền: GET công khai cho anonymous; còn lại 401; /me/won cần đăng nhập")
    void securityMatrix() throws Exception {
        long id = startAuction();
        mockMvc.perform(get("/api/v1/auctions/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.winner_id").doesNotExist());
        mockMvc.perform(get("/api/v1/auctions/" + id + "/bids")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auctions/by-product/" + product.getProductId())).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auctions/me/won")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auctions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auctions/" + id + "/bids").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":60000}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auctions/" + id + "/checkout").contentType(MediaType.APPLICATION_JSON)
                .content(CHECKOUT)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auctions/999999")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tạo phiên: ACTIVE ngay, snapshot giá khởi điểm, listing AUCTION, AUCTION_STARTED sau commit; điều kiện chặn")
    void enableAuction() throws Exception {
        json(bob, "/api/v1/auctions", "{\"product_id\":" + product.getProductId() + ",\"bid_step\":1000,\"duration_minutes\":10}")
                .andExpect(status().isNotFound());
        json(seller, "/api/v1/auctions", "{\"product_id\":" + product.getProductId() + ",\"bid_step\":0,\"duration_minutes\":10}")
                .andExpect(status().isUnprocessableContent());
        json(seller, "/api/v1/auctions", "{\"product_id\":" + product.getProductId() + ",\"bid_step\":1000}")
                .andExpect(status().isBadRequest());

        long id = startAuction();
        mockMvc.perform(get("/api/v1/auctions/" + id))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.starting_price").value(50000))
                .andExpect(jsonPath("$.current_price").value(50000))
                .andExpect(jsonPath("$.minimum_next_bid").value(50000))
                .andExpect(jsonPath("$.bid_count").value(0))
                .andExpect(jsonPath("$.winner_checkout_state").value("NOT_APPLICABLE"));
        assertEquals("AUCTION", jdbc.queryForObject("SELECT listing_type FROM products WHERE product_id = ?", String.class,
                product.getProductId()));
        assertTrue(TrackATestConfig.AUCTION_EVENTS.stream().anyMatch(e -> "AUCTION_STARTED".equals(e.get("event"))));
        // đã có phiên mở → không tạo thêm
        json(seller, "/api/v1/auctions", "{\"product_id\":" + product.getProductId() + ",\"bid_step\":1000,\"duration_minutes\":10}")
                .andExpect(status().isBadRequest());
        // hàng nhiều tồn kho không đấu giá được
        Product multi = newProduct(seller, category, "Nhiều hàng", "1000", 3);
        json(seller, "/api/v1/auctions", "{\"product_id\":" + multi.getProductId() + ",\"bid_step\":100,\"duration_minutes\":10}")
                .andExpect(status().isBadRequest());
        // sản phẩm đấu giá không mua thẳng được
        json(alice, "/api/v1/orders", "{\"product_id\":" + product.getProductId() + ",\"payment_method\":\"CASH_ON_MEETUP\","
                + "\"meetup_place\":\"x\",\"contact_name\":\"A\",\"contact_phone\":\"0987654321\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Đặt giá: luật giá tối thiểu/bước giá, người bán không được đặt, thông báo BID/OUTBID, BID_PLACED sau commit, ẩn danh")
    void placeBidRules() throws Exception {
        long id = startAuction();
        bid(seller, id, 70000).andExpect(status().isBadRequest());
        bid(alice, id, 49999).andExpect(status().isBadRequest());
        json(alice, "/api/v1/auctions/" + id + "/bids", "{\"amount\":0}").andExpect(status().isUnprocessableContent());

        bid(alice, id, 50000).andExpect(status().isCreated())
                .andExpect(jsonPath("$.bidder_display").value("User A."))
                .andExpect(jsonPath("$.is_current_user").value(true))
                .andExpect(jsonPath("$.bidder_id").doesNotExist());
        assertEquals(1, notificationCount(seller.getUserId(), "AUCTION_BID"));
        assertEquals(0, notificationCount(alice.getUserId(), "AUCTION_OUTBID"), "bid đầu tiên không có OUTBID");
        bid(bob, id, 69999).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Giá đặt phải tối thiểu là 70,000 đ."));
        bid(bob, id, 70000).andExpect(status().isCreated());
        assertEquals(1, notificationCount(alice.getUserId(), "AUCTION_OUTBID"));
        bid(bob, id, 90000).andExpect(status().isCreated());   // tự nâng giá
        assertEquals(1, notificationCount(alice.getUserId(), "AUCTION_OUTBID"));
        assertEquals(0, notificationCount(bob.getUserId(), "AUCTION_OUTBID"), "tự nâng giá không OUTBID");
        assertEquals(3, notificationCount(seller.getUserId(), "AUCTION_BID"));
        assertTrue(TrackATestConfig.AUCTION_EVENTS.stream().anyMatch(e -> "BID_PLACED".equals(e.get("event"))
                && "90000".equals(String.valueOf(e.get("highest_bid"))) && ((Number) e.get("bid_count")).intValue() == 3));

        mockMvc.perform(auth(get("/api/v1/auctions/" + id), bob))
                .andExpect(jsonPath("$.current_price").value(90000))
                .andExpect(jsonPath("$.highest_bid").value(90000))
                .andExpect(jsonPath("$.bid_count").value(3))
                .andExpect(jsonPath("$.minimum_next_bid").value(110000))
                .andExpect(jsonPath("$.is_current_user_highest_bidder").value(true))
                .andExpect(jsonPath("$.highest_bidder_display").value("User B."));
        mockMvc.perform(auth(get("/api/v1/auctions/" + id), alice))
                .andExpect(jsonPath("$.is_current_user_highest_bidder").value(false));
    }

    @Test
    @DisplayName("Lịch sử bid: keyset (amount DESC, created_at ASC, bid_id ASC), is_current_user/is_highest, ẩn danh")
    void bidHistoryKeyset() throws Exception {
        long id = startAuction();
        bid(alice, id, 50000).andExpect(status().isCreated());
        bid(bob, id, 70000).andExpect(status().isCreated());
        bid(alice, id, 90000).andExpect(status().isCreated());

        MvcResult p1 = mockMvc.perform(auth(get("/api/v1/auctions/" + id + "/bids").param("limit", "2"), alice))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].amount").value(90000))
                .andExpect(jsonPath("$.items[0].is_highest").value(true))
                .andExpect(jsonPath("$.items[0].is_current_user").value(true))
                .andExpect(jsonPath("$.items[1].amount").value(70000))
                .andExpect(jsonPath("$.items[1].is_current_user").value(false))
                .andExpect(jsonPath("$.has_more").value(true)).andReturn();
        String cursor = JsonPath.read(p1.getResponse().getContentAsString(), "$.next_cursor");
        mockMvc.perform(get("/api/v1/auctions/" + id + "/bids").param("limit", "2").param("cursor", cursor))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].amount").value(50000))
                .andExpect(jsonPath("$.items[0].is_current_user").value(false))
                .andExpect(jsonPath("$.has_more").value(false));
        mockMvc.perform(get("/api/v1/auctions/" + id + "/bids").param("cursor", "garbage")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/auctions/" + id + "/bids").param("limit", "51")).andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TRANH CHẤP: 8 người cùng đặt đúng 1 mức giá (nhiều thread, khoá FOR UPDATE) → đúng 1 bid thành công")
    void concurrentSameAmountBids() throws Exception {
        long id = startAuction();
        int n = 8;
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            tokens.add(token(newUser("bidder" + i)));
        }
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> fs = new ArrayList<>();
        for (String tk : tokens) {
            fs.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return mockMvc.perform(post("/api/v1/auctions/" + id + "/bids").header("Authorization", "Bearer " + tk)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":50000}")).andReturn().getResponse().getStatus();
            }));
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();
        int ok = 0;
        int rejected = 0;
        for (Future<Integer> f : fs) {
            int s = f.get(60, TimeUnit.SECONDS);
            if (s == 201) {
                ok++;
            } else if (s == 400) {
                rejected++;
            }
        }
        pool.shutdownNow();
        assertEquals(1, ok);
        assertEquals(n - 1, rejected);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM auction_bids WHERE auction_id = ?", Integer.class, id));
    }

    @Test
    @DisplayName("TRANH CHẤP: chuỗi bid tăng dần từ nhiều thread → mọi bid được lưu luôn cách nhau >= bid_step")
    void concurrentBidsRespectStep() throws Exception {
        long id = startAuction();
        int n = 6;
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            tokens.add(token(newUser("climber" + i)));
        }
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String tk = tokens.get(i);
            long amount = 50000 + 10000L * i;   // chênh nhỏ hơn bid_step 20000
            fs.add(pool.submit(() -> {
                go.await();
                return mockMvc.perform(post("/api/v1/auctions/" + id + "/bids").header("Authorization", "Bearer " + tk)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + "}")).andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        for (Future<Integer> f : fs) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdownNow();
        List<Long> amounts = jdbc.queryForList("SELECT amount::bigint FROM auction_bids WHERE auction_id = ? ORDER BY bid_id", Long.class, id);
        assertFalse(amounts.isEmpty());
        for (int i = 1; i < amounts.size(); i++) {
            assertTrue(amounts.get(i) - amounts.get(i - 1) >= 20000, "bid sau phải >= bid trước + bid_step: " + amounts);
        }
        assertEquals(amounts.size(), jdbc.queryForObject("SELECT count(*) FROM auction_bids", Integer.class));
    }

    @Test
    @DisplayName("Huỷ phiên: chủ sản phẩm huỷ khi chưa có bid → CANCELLED + FIXED_PRICE; đã có bid / người lạ bị chặn")
    void cancelAuction() throws Exception {
        long id = startAuction();
        json(bob, "/api/v1/auctions/" + id + "/cancel", "{}").andExpect(status().isForbidden());
        bid(alice, id, 50000).andExpect(status().isCreated());
        json(seller, "/api/v1/auctions/" + id + "/cancel", "{}").andExpect(status().isBadRequest());

        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM auction_bids");
        TrackATestConfig.AUCTION_EVENTS.clear();
        json(seller, "/api/v1/auctions/" + id + "/cancel", "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertEquals("FIXED_PRICE", jdbc.queryForObject("SELECT listing_type FROM products WHERE product_id = ?", String.class,
                product.getProductId()));
        assertTrue(TrackATestConfig.AUCTION_EVENTS.stream().anyMatch(e -> "AUCTION_CANCELLED".equals(e.get("event"))));
        json(seller, "/api/v1/auctions/" + id + "/cancel", "{}").andExpect(status().isBadRequest());
        bid(alice, id, 50000).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/auctions/by-product/" + product.getProductId()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("Hết hạn có người thắng: đóng tại chỗ khi GET, giữ hàng, AUCTION_WON/ENDED, không lộ winner_id; /me/won; checkout tạo đúng 1 đơn")
    void closeAndWinnerCheckout() throws Exception {
        long id = startAuction();
        bid(alice, id, 50000).andExpect(status().isCreated());
        bid(bob, id, 70000).andExpect(status().isCreated());
        expireAuction(id);
        TrackATestConfig.AUCTION_EVENTS.clear();

        mockMvc.perform(auth(get("/api/v1/auctions/" + id), bob))
                .andExpect(jsonPath("$.status").value("ENDED"))
                .andExpect(jsonPath("$.has_winner").value(true))
                .andExpect(jsonPath("$.final_price").value(70000))
                .andExpect(jsonPath("$.is_current_user_winner").value(true))
                .andExpect(jsonPath("$.winner_checkout_state").value("WAITING"))
                .andExpect(jsonPath("$.winner_checkout_deadline").isNotEmpty())
                .andExpect(jsonPath("$.winner_id").doesNotExist());
        assertEquals(1, reserved(product.getProductId()));
        assertEquals(1, notificationCount(bob.getUserId(), "AUCTION_WON"));
        assertEquals(1, notificationCount(seller.getUserId(), "AUCTION_ENDED"));
        assertEquals(1, TrackATestConfig.AUCTION_EVENTS.stream().filter(e -> "AUCTION_ENDED".equals(e.get("event"))).count());
        // GET lại: idempotent, không thông báo trùng
        mockMvc.perform(get("/api/v1/auctions/" + id)).andExpect(jsonPath("$.is_current_user_winner").value(false));
        assertEquals(1, notificationCount(bob.getUserId(), "AUCTION_WON"));
        bid(alice, id, 200000).andExpect(status().isBadRequest());

        mockMvc.perform(auth(get("/api/v1/auctions/me/won"), bob)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].auction_id").value(id)).andExpect(jsonPath("$[0].final_price").value(70000));
        mockMvc.perform(auth(get("/api/v1/auctions/me/won"), alice)).andExpect(jsonPath("$", hasSize(0)));

        json(alice, "/api/v1/auctions/" + id + "/checkout", CHECKOUT).andExpect(status().isForbidden());
        json(seller, "/api/v1/auctions/" + id + "/checkout", CHECKOUT).andExpect(status().isForbidden());
        TrackATestConfig.USER_EVENTS.clear();
        MvcResult r = json(bob, "/api/v1/auctions/" + id + "/checkout", CHECKOUT).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total_amount").value(70000))
                .andExpect(jsonPath("$.buyer_id").value(bob.getUserId()))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.items[0].price_at_purchase").value(70000)).andReturn();
        long orderId = ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.order_id")).longValue();
        assertEquals(id, jdbc.queryForObject("SELECT auction_id FROM orders WHERE order_id = ?", Long.class, orderId));
        assertEquals(1, reserved(product.getProductId()), "tái sử dụng reservation, không tăng lần 2");
        assertEquals(1, notificationCount(seller.getUserId(), "NEW_ORDER"));
        assertTrue(TrackATestConfig.USER_EVENTS.stream().anyMatch(e -> "AUCTION_WINNER_CHECKED_OUT".equals(e.get("type"))));
        assertTrue(TrackATestConfig.USER_EVENTS.stream().anyMatch(e -> "ORDER_CREATED".equals(e.get("type"))));

        json(bob, "/api/v1/auctions/" + id + "/checkout", CHECKOUT).andExpect(status().isBadRequest());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class));
        mockMvc.perform(get("/api/v1/auctions/" + id))
                .andExpect(jsonPath("$.winner_checkout_state").value("ORDER_CREATED"))
                .andExpect(jsonPath("$.winner_order_id").value(orderId));
        mockMvc.perform(auth(get("/api/v1/auctions/me/won"), bob)).andExpect(jsonPath("$", hasSize(0)));

        // đơn sinh từ đấu giá đi tiếp vòng đời thường: seller xác nhận → trả tiền → hoàn tất → trừ kho
        json(seller, "/api/v1/orders/" + orderId + "/confirm", "{}").andExpect(status().isOk());
        json(seller, "/api/v1/orders/" + orderId + "/confirm-payment", "{}").andExpect(status().isOk());
        json(bob, "/api/v1/orders/" + orderId + "/complete", "{}").andExpect(status().isOk());
        assertEquals(0, stock(product.getProductId()));
        assertEquals(0, reserved(product.getProductId()));
    }

    @Test
    @DisplayName("Hết hạn không có bid: ENDED/NO_BIDS, không giữ hàng, chỉ thông báo seller")
    void closeWithoutBids() throws Exception {
        long id = startAuction();
        expireAuction(id);
        mockMvc.perform(get("/api/v1/auctions/" + id))
                .andExpect(jsonPath("$.status").value("ENDED"))
                .andExpect(jsonPath("$.has_winner").value(false))
                .andExpect(jsonPath("$.winner_checkout_state").value("NO_BIDS"));
        assertEquals(0, reserved(product.getProductId()));
        assertEquals(1, notificationCount(seller.getUserId(), "AUCTION_ENDED"));
        json(alice, "/api/v1/auctions/" + id + "/checkout", CHECKOUT).andExpect(status().isForbidden());
        // sau khi kết thúc có thể chuyển về giá cố định
        json(seller, "/api/v1/auctions/products/" + product.getProductId() + "/convert-fixed-price", "{}")
                .andExpect(status().isOk());
        assertEquals("FIXED_PRICE", jdbc.queryForObject("SELECT listing_type FROM products WHERE product_id = ?", String.class,
                product.getProductId()));
    }

    @Test
    @DisplayName("Worker: close sweep đóng phiên hết hạn, winner-timeout sweep nhả giữ hàng khi quá hạn checkout; idempotent")
    void workerSweeps() throws Exception {
        long id = startAuction();
        bid(alice, id, 60000).andExpect(status().isCreated());
        expireAuction(id);

        worker.runAllSweeps();
        assertEquals("ENDED", jdbc.queryForObject("SELECT status FROM auctions WHERE auction_id = ?", String.class, id));
        assertEquals(1, reserved(product.getProductId()));
        assertEquals(1, notificationCount(alice.getUserId(), "AUCTION_WON"));
        worker.runAllSweeps();   // idempotent
        assertEquals(1, notificationCount(alice.getUserId(), "AUCTION_WON"));
        assertEquals(1, notificationCount(seller.getUserId(), "AUCTION_ENDED"));

        // chưa quá hạn checkout → không nhả
        assertEquals(1, reserved(product.getProductId()));
        expireWinnerDeadline(id);
        worker.runAllSweeps();
        assertEquals(0, reserved(product.getProductId()));
        json(alice, "/api/v1/auctions/" + id + "/checkout", CHECKOUT).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Đã hết thời hạn hoàn tất đặt mua cho phiên đấu giá này."));
        mockMvc.perform(get("/api/v1/auctions/" + id)).andExpect(jsonPath("$.winner_checkout_state").value("EXPIRED"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class));
    }

    @Test
    @DisplayName("Phiên SCHEDULED (legacy starts_at/ends_at): worker kích hoạt khi đến giờ, bid sớm bị chặn")
    void scheduledAuctionActivation() throws Exception {
        String starts = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusHours(1).toString();
        String ends = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusHours(3).toString();
        MvcResult r = json(seller, "/api/v1/auctions", "{\"product_id\":" + product.getProductId()
                + ",\"bid_step\":1000,\"starts_at\":\"" + starts + "\",\"ends_at\":\"" + ends + "\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SCHEDULED")).andReturn();
        long id = ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.auction_id")).longValue();
        bid(alice, id, 50000).andExpect(status().isBadRequest());

        Auction a = auctionRepository.findById(id).orElseThrow();
        a.setStartsAt(utcNow().minusMinutes(1));
        auctionRepository.save(a);
        TrackATestConfig.AUCTION_EVENTS.clear();
        worker.runAllSweeps();
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM auctions WHERE auction_id = ?", String.class, id));
        assertTrue(TrackATestConfig.AUCTION_EVENTS.stream().anyMatch(e -> "AUCTION_STARTED".equals(e.get("event"))));
        bid(alice, id, 50000).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Chuyển về giá cố định bị chặn khi đang có phiên hoạt động hoặc đang giữ hàng; người lạ nhận 404")
    void convertFixedPriceGuards() throws Exception {
        long id = startAuction();
        json(seller, "/api/v1/auctions/products/" + product.getProductId() + "/convert-fixed-price", "{}")
                .andExpect(status().isBadRequest());
        json(bob, "/api/v1/auctions/products/" + product.getProductId() + "/convert-fixed-price", "{}")
                .andExpect(status().isNotFound());
        bid(alice, id, 50000).andExpect(status().isCreated());
        expireAuction(id);
        mockMvc.perform(get("/api/v1/auctions/" + id)).andExpect(status().isOk());   // đóng → giữ hàng cho alice
        json(seller, "/api/v1/auctions/products/" + product.getProductId() + "/convert-fixed-price", "{}")
                .andExpect(status().isBadRequest());
    }
}
