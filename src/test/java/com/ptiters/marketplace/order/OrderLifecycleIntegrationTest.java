package com.ptiters.marketplace.order;

import com.jayway.jsonpath.JsonPath;
import com.ptiters.marketplace.category.Category;
import com.ptiters.marketplace.product.Product;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.worker.OrderAutoCancelWorker;
import com.ptiters.marketplace.worker.SweepResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Vòng đời đơn hàng trên PostgreSQL THẬT (không mock): trạng thái, kho, thông báo, quyền, tranh chấp, worker. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TrackATestConfig.class)
class OrderLifecycleIntegrationTest extends TrackATestSupport {

    @Autowired
    private OrderAutoCancelWorker worker;

    private User seller;
    private User buyer;
    private User outsider;
    private Category category;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        TrackATestConfig.USER_EVENTS.clear();
        seller = newUser("seller");
        buyer = newUser("buyer");
        outsider = newUser("outsider");
        category = newCategory();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
        assertAllTablesEmpty();
    }

    // ------------------------------------------------------------------ helpers

    private static String orderJson(Long productId, int qty, String payment, String fulfillment) {
        String pay = payment == null ? "" : "\"payment_method\":\"" + payment + "\",";
        String where = "SHIPPING".equals(fulfillment)
                ? "\"shipping_address\":\"KTX B2 PTIT\","
                : "\"meetup_place\":\"Cổng trường PTIT\",";
        return "{\"product_id\":" + productId + ",\"quantity\":" + qty + "," + pay
                + "\"fulfillment_method\":\"" + fulfillment + "\"," + where
                + "\"contact_name\":\"Nguyen Van B\",\"contact_phone\":\"0987654321\"}";
    }

    private ResultActions postJson(User u, String url, String json) throws Exception {
        return mockMvc.perform(auth(post(url), u).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long createOrder(User by, Product p, String payment, String fulfillment) throws Exception {
        MvcResult r = postJson(by, "/api/v1/orders", orderJson(p.getProductId(), 1, payment, fulfillment))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.order_id")).longValue();
    }

    private ResultActions action(User u, long orderId, String verb) throws Exception {
        return mockMvc.perform(auth(post("/api/v1/orders/" + orderId + "/" + verb), u)
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    private ResultActions cancel(User u, long orderId, String reason) throws Exception {
        return postJson(u, "/api/v1/orders/" + orderId + "/cancel", "{\"cancel_reason\":\"" + reason + "\"}");
    }

    private void giveSellerBank() {
        jdbc.update("UPDATE users SET bank_bin='970436', bank_account_number='0123456789', bank_account_name='NGUYEN VAN A' "
                + "WHERE user_id = ?", seller.getUserId());
    }

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};

    // ------------------------------------------------------------------ auth

    @Test
    @DisplayName("401 khi thiếu token ở mọi endpoint đơn hàng")
    void unauthenticatedIs401() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/orders/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/orders/me/buying")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/orders/1/cancel").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ create

    @Test
    @DisplayName("Tạo đơn CASH_ON_MEETUP: 201, giữ hàng, history, NEW_ORDER cho seller, ORDER_CREATED sau commit")
    void createCashOrder() throws Exception {
        Product p = newProduct(seller, category, "Compa", "25000", 10);
        MvcResult r = postJson(buyer, "/api/v1/orders", orderJson(p.getProductId(), 2, "CASH_ON_MEETUP", "MEETUP"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.payment_status").value("UNPAID"))
                .andExpect(jsonPath("$.payment_method").value("CASH_ON_MEETUP"))
                .andExpect(jsonPath("$.total_amount").value(50000))
                .andExpect(jsonPath("$.order_code", matchesPattern("ORD-\\d{6}-[0-9A-F]{6}")))
                .andExpect(jsonPath("$.buyer_id").value(buyer.getUserId()))
                .andExpect(jsonPath("$.seller_id").value(seller.getUserId()))
                .andExpect(jsonPath("$.buyer_public_id").isNotEmpty())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].price_at_purchase").value(25000))
                .andExpect(jsonPath("$.status_history", hasSize(1)))
                .andExpect(jsonPath("$.status_history[0].to_status").value("PENDING"))
                .andExpect(jsonPath("$.auto_cancel_at").isNotEmpty())
                .andExpect(jsonPath("$.payment_bank").doesNotExist())
                .andReturn();
        long orderId = ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.order_id")).longValue();

        assertEquals(10, stock(p.getProductId()));
        assertEquals(2, reserved(p.getProductId()));
        assertEquals(1, notificationCount(seller.getUserId(), "NEW_ORDER"));
        assertEquals(0, notificationCount(buyer.getUserId(), "NEW_ORDER"));
        assertTrue(TrackATestConfig.USER_EVENTS.stream().anyMatch(e ->
                "ORDER_CREATED".equals(e.get("type")) && Long.valueOf(orderId).equals(((Number) e.get("order_id")).longValue())
                        && seller.getUserId().equals(e.get("_user_id"))), "ORDER_CREATED phải được phát sau commit");
    }

    @Test
    @DisplayName("Sản phẩm miễn phí: tự PAID + payment_method OTHER; không cần/không được tải biên nhận")
    void freeProductAutoPaid() throws Exception {
        Product p = newProduct(seller, category, "Tặng sách", "0", 1);
        long id = createOrder(buyer, p, null, "MEETUP");
        mockMvc.perform(auth(get("/api/v1/orders/" + id), buyer))
                .andExpect(jsonPath("$.payment_method").value("OTHER"))
                .andExpect(jsonPath("$.payment_status").value("PAID"))
                .andExpect(jsonPath("$.paid_confirmed_at").isNotEmpty())
                .andExpect(jsonPath("$.total_amount").value(0));
        action(seller, id, "confirm").andExpect(status().isOk());
        mockMvc.perform(auth(multipart("/api/v1/orders/" + id + "/receipt")
                        .file(new MockMultipartFile("file", "r.png", "image/png", PNG)), buyer))
                .andExpect(status().isBadRequest());
        // Đơn miễn phí đã PAID → hoàn tất được ngay khi CONFIRMED (MEETUP)
        action(buyer, id, "complete").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        assertEquals(0, stock(p.getProductId()));
        assertEquals(0, reserved(p.getProductId()));
        assertEquals("SOLD_OUT", jdbc.queryForObject("SELECT status FROM products WHERE product_id = ?", String.class,
                p.getProductId()));
    }

    @Test
    @DisplayName("Validation: thiếu contact_name → 422; MEETUP thiếu địa điểm → 422; sản phẩm có phí thiếu payment_method → 400")
    void createValidation() throws Exception {
        Product p = newProduct(seller, category, "Bút", "10000", 5);
        postJson(buyer, "/api/v1/orders", "{\"product_id\":" + p.getProductId() + ",\"contact_phone\":\"0987654321\","
                + "\"payment_method\":\"CASH_ON_MEETUP\",\"meetup_place\":\"x\"}").andExpect(status().isUnprocessableContent());
        postJson(buyer, "/api/v1/orders", "{\"product_id\":" + p.getProductId() + ",\"contact_name\":\"B\","
                + "\"contact_phone\":\"0987654321\",\"payment_method\":\"CASH_ON_MEETUP\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Vui lòng nhập địa điểm hẹn gặp."));
        postJson(buyer, "/api/v1/orders", orderJson(p.getProductId(), 1, null, "MEETUP"))
                .andExpect(status().isBadRequest());
        postJson(buyer, "/api/v1/orders", orderJson(p.getProductId(), 1, "OTHER", "MEETUP"))
                .andExpect(status().isBadRequest());
        assertEquals(0, reserved(p.getProductId()));
    }

    @Test
    @DisplayName("Không tự mua hàng của mình; sản phẩm không tồn tại/chưa duyệt → 404")
    void selfBuyAndUnavailable() throws Exception {
        Product p = newProduct(seller, category, "Sách", "30000", 3);
        postJson(seller, "/api/v1/orders", orderJson(p.getProductId(), 1, "CASH_ON_MEETUP", "MEETUP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Không thể mua sản phẩm của chính mình."));
        postJson(buyer, "/api/v1/orders", orderJson(999999L, 1, "CASH_ON_MEETUP", "MEETUP")).andExpect(status().isNotFound());
        jdbc.update("UPDATE products SET approval_status='DRAFT' WHERE product_id = ?", p.getProductId());
        postJson(buyer, "/api/v1/orders", orderJson(p.getProductId(), 1, "CASH_ON_MEETUP", "MEETUP"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Bị hạn chế BUY: 403 với detail dạng object {code: BUY_RESTRICTED}; người bán bị SELL → 404")
    void capabilityRestrictions() throws Exception {
        Product p = newProduct(seller, category, "Áo", "50000", 3);
        restrict(buyer, "BUY", outsider);
        postJson(buyer, "/api/v1/orders", orderJson(p.getProductId(), 1, "CASH_ON_MEETUP", "MEETUP"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail.code").value("BUY_RESTRICTED"))
                .andExpect(jsonPath("$.detail.message").isNotEmpty());
        assertEquals(0, reserved(p.getProductId()));

        // buyer khác mua hàng của seller bị SELL-restrict → ẩn như không tồn tại
        jdbc.update("DELETE FROM user_capability_restrictions");
        restrict(seller, "SELL", outsider);
        postJson(buyer, "/api/v1/orders", orderJson(p.getProductId(), 1, "CASH_ON_MEETUP", "MEETUP"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ lifecycle

    @Test
    @DisplayName("MEETUP + tiền mặt: PENDING→CONFIRMED→PAID→COMPLETED, trừ kho thật, thông báo đúng người")
    void meetupCashLifecycle() throws Exception {
        Product p = newProduct(seller, category, "Máy tính Casio", "200000", 10);
        long id = createOrder(buyer, p, "CASH_ON_MEETUP", "MEETUP");

        // người mua không được xác nhận
        action(buyer, id, "confirm").andExpect(status().isNotFound());

        action(seller, id, "confirm").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.auto_cancel_at").doesNotExist())
                .andExpect(jsonPath("$.confirmed_at").isNotEmpty());
        // xác nhận lại → 400
        action(seller, id, "confirm").andExpect(status().isBadRequest());
        // chưa thanh toán → chưa hoàn tất được
        action(buyer, id, "complete").andExpect(status().isBadRequest());
        // MEETUP không có trạng thái DELIVERING
        action(seller, id, "deliver").andExpect(status().isBadRequest());

        action(seller, id, "confirm-payment").andExpect(status().isOk())
                .andExpect(jsonPath("$.payment_status").value("PAID"));
        action(seller, id, "confirm-payment").andExpect(status().isOk());   // idempotent

        action(buyer, id, "complete").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completed_at").isNotEmpty())
                .andExpect(jsonPath("$.status_history", hasSize(4)));
        action(buyer, id, "complete").andExpect(status().isBadRequest());

        assertEquals(9, stock(p.getProductId()));
        assertEquals(0, reserved(p.getProductId()));
        // buyer: CONFIRMED + PAYMENT; seller: COMPLETED (người hoàn tất là buyer → seller nhận)
        assertEquals(2, notificationCount(buyer.getUserId(), "ORDER_STATUS"));
        assertEquals(1, notificationCount(seller.getUserId(), "ORDER_STATUS"));
        assertTrue(TrackATestConfig.USER_EVENTS.stream().anyMatch(e -> "ORDER_UPDATED".equals(e.get("type"))
                && "COMPLETED".equals(e.get("reason"))));
    }

    @Test
    @DisplayName("SHIPPING + chuyển khoản: snapshot ngân hàng + VietQR, biên nhận, xác nhận tiền, giao, hoàn tất")
    void shippingBankTransferLifecycle() throws Exception {
        Product p = newProduct(seller, category, "Laptop cũ", "5000000", 1);
        long id = createOrder(buyer, p, "BANK_TRANSFER", "SHIPPING");

        // PENDING: chưa được upload biên nhận
        mockMvc.perform(auth(multipart("/api/v1/orders/" + id + "/receipt")
                        .file(new MockMultipartFile("file", "r.png", "image/png", PNG)), buyer))
                .andExpect(status().isBadRequest());
        // người bán chưa có tài khoản nhận tiền → không xác nhận được
        action(seller, id, "confirm").andExpect(status().isBadRequest());

        giveSellerBank();
        action(seller, id, "confirm").andExpect(status().isOk())
                .andExpect(jsonPath("$.payment_bank.bank_bin").value("970436"))
                .andExpect(jsonPath("$.payment_bank.bank_code").value("VCB"))
                .andExpect(jsonPath("$.payment_bank.account_number").value("0123456789"))
                .andExpect(jsonPath("$.payment_bank.qr_url", startsWith("https://img.vietqr.io/image/970436-0123456789-compact2.png?amount=5000000&addInfo=ORD-")));

        // chưa PAID → không giao được; chưa có biên nhận → không xác nhận tiền được
        action(seller, id, "deliver").andExpect(status().isBadRequest());
        action(seller, id, "confirm-payment").andExpect(status().isBadRequest());

        // file không phải ảnh thật (Content-Type giả) → 422 từ MediaValidator
        mockMvc.perform(auth(multipart("/api/v1/orders/" + id + "/receipt")
                        .file(new MockMultipartFile("file", "r.png", "image/png", "not-an-image-bytes".getBytes())), buyer))
                .andExpect(status().isUnprocessableContent());
        // Content-Type không hợp lệ → 400
        mockMvc.perform(auth(multipart("/api/v1/orders/" + id + "/receipt")
                        .file(new MockMultipartFile("file", "r.pdf", "application/pdf", PNG)), buyer))
                .andExpect(status().isBadRequest());
        // người ngoài / người bán không tải được biên nhận (chỉ buyer)
        mockMvc.perform(auth(multipart("/api/v1/orders/" + id + "/receipt")
                        .file(new MockMultipartFile("file", "r.png", "image/png", PNG)), seller))
                .andExpect(status().isNotFound());

        mockMvc.perform(auth(multipart("/api/v1/orders/" + id + "/receipt")
                        .file(new MockMultipartFile("file", "r.png", "image/png", PNG)), buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment_status").value("VERIFYING"))
                .andExpect(jsonPath("$.payment_receipt_url", startsWith("https://media.local.test/")))
                .andExpect(jsonPath("$.receipt_uploaded_at").isNotEmpty());

        // đang VERIFYING: không huỷ được
        cancel(buyer, id, "đổi ý").andExpect(status().isBadRequest());

        action(seller, id, "confirm-payment").andExpect(status().isOk())
                .andExpect(jsonPath("$.payment_status").value("PAID"));
        action(seller, id, "deliver").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERING"));
        // đang giao: không huỷ được; người mua hoàn tất
        cancel(seller, id, "x").andExpect(status().isBadRequest());
        action(buyer, id, "complete").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));

        assertEquals(0, stock(p.getProductId()));
        assertEquals("SOLD_OUT", jdbc.queryForObject("SELECT status FROM products WHERE product_id = ?", String.class,
                p.getProductId()));
        // lịch sử: PENDING, CONFIRMED, receipt, payment, DELIVERING, COMPLETED
        mockMvc.perform(auth(get("/api/v1/orders/" + id), buyer))
                .andExpect(jsonPath("$.status_history", hasSize(6)));
    }

    @Test
    @DisplayName("Hoàn tất đơn làm hết hàng → xoá yêu thích liên quan")
    void completeRemovesFavorites() throws Exception {
        Product p = newProduct(seller, category, "Món cuối", "10000", 1);
        jdbc.update("INSERT INTO favorites(user_id, product_id) VALUES (?, ?)", outsider.getUserId(), p.getProductId());
        long id = createOrder(buyer, p, "CASH_ON_MEETUP", "MEETUP");
        action(seller, id, "confirm").andExpect(status().isOk());
        action(seller, id, "confirm-payment").andExpect(status().isOk());
        action(seller, id, "complete").andExpect(status().isOk());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM favorites WHERE product_id = ?", Integer.class,
                p.getProductId()));
    }

    // ------------------------------------------------------------------ cancel / stock

    @Test
    @DisplayName("Huỷ đơn: hoàn reserved_stock, cancelled_by, thông báo cho bên kia (không lộ lý do), idempotent")
    void cancelReleasesStock() throws Exception {
        Product p = newProduct(seller, category, "Balo", "150000", 3);
        long id = createOrder(buyer, p, "CASH_ON_MEETUP", "MEETUP");
        assertEquals(1, reserved(p.getProductId()));

        // cancel_reason bắt buộc
        postJson(buyer, "/api/v1/orders/" + id + "/cancel", "{\"cancel_reason\":\"  \"}").andExpect(status().isUnprocessableContent());
        postJson(buyer, "/api/v1/orders/" + id + "/cancel", "{}").andExpect(status().isUnprocessableContent());

        cancel(buyer, id, "Bí mật lý do ABC").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelled_by").value("BUYER"))
                .andExpect(jsonPath("$.cancel_reason").value("Bí mật lý do ABC"))
                .andExpect(jsonPath("$.cancelled_at").isNotEmpty());
        assertEquals(0, reserved(p.getProductId()));
        assertEquals(3, stock(p.getProductId()));
        assertEquals(1, notificationCount(seller.getUserId(), "ORDER_STATUS"));
        assertEquals(0, notificationCount(buyer.getUserId(), "ORDER_STATUS"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE body LIKE '%ABC%'", Integer.class));

        // idempotent: huỷ lại → 200, không hoàn kho lần 2, không thêm thông báo
        cancel(seller, id, "lần hai").andExpect(status().isOk()).andExpect(jsonPath("$.cancelled_by").value("BUYER"));
        assertEquals(0, reserved(p.getProductId()));
        assertEquals(1, notificationCount(seller.getUserId(), "ORDER_STATUS"));
        // đã huỷ thì không xác nhận/hoàn tất được
        action(seller, id, "confirm").andExpect(status().isBadRequest());
        action(buyer, id, "complete").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Người bán huỷ đơn đã CONFIRMED (chưa trả tiền) được; đơn đã PAID không huỷ được")
    void cancelRulesAfterConfirm() throws Exception {
        Product p = newProduct(seller, category, "Giày", "300000", 5);
        long a = createOrder(buyer, p, "CASH_ON_MEETUP", "MEETUP");
        action(seller, a, "confirm").andExpect(status().isOk());
        cancel(seller, a, "hết hàng").andExpect(status().isOk()).andExpect(jsonPath("$.cancelled_by").value("SELLER"));
        assertEquals(2, notificationCount(buyer.getUserId(), "ORDER_STATUS"), "buyer: 1 (confirmed) + 1 (cancelled)");

        long b = createOrder(buyer, p, "CASH_ON_MEETUP", "MEETUP");
        action(seller, b, "confirm").andExpect(status().isOk());
        action(seller, b, "confirm-payment").andExpect(status().isOk());
        cancel(buyer, b, "đổi ý").andExpect(status().isBadRequest());
        assertEquals(1, reserved(p.getProductId()));
    }

    @Test
    @DisplayName("Hết tồn kho khả dụng → 400 với số lượng còn lại; nhiều đơn cộng dồn reserved_stock")
    void stockExhaustion() throws Exception {
        Product p = newProduct(seller, category, "Hàng hiếm", "10000", 2);
        createOrder(buyer, p, "CASH_ON_MEETUP", "MEETUP");
        postJson(outsider, "/api/v1/orders", orderJson(p.getProductId(), 2, "CASH_ON_MEETUP", "MEETUP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Số lượng tồn kho khả dụng không đủ (còn 1 sản phẩm)."));
        createOrder(outsider, p, "CASH_ON_MEETUP", "MEETUP");
        postJson(buyer, "/api/v1/orders", orderJson(p.getProductId(), 1, "CASH_ON_MEETUP", "MEETUP"))
                .andExpect(status().isBadRequest());
        assertEquals(2, reserved(p.getProductId()));
    }

    @Test
    @DisplayName("TRANH CHẤP: 8 người cùng mua món hàng cuối (nhiều thread, PostgreSQL thật) → đúng 1 đơn thành công, không oversell")
    void concurrentLastItemPurchase() throws Exception {
        Product p = newProduct(seller, category, "Món cuối cùng", "99000", 1);
        int n = 8;
        List<User> buyers = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            buyers.add(newUser("racer" + i));
        }
        List<String> tokens = buyers.stream().map(this::token).toList();   // tạo token trước, ngoài thread
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String tk = tokens.get(i);
            results.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return mockMvc.perform(post("/api/v1/orders")
                                .header("Authorization", "Bearer " + tk)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(orderJson(p.getProductId(), 1, "CASH_ON_MEETUP", "MEETUP")))
                        .andReturn().getResponse().getStatus();
            }));
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();
        int created = 0;
        int rejected = 0;
        for (Future<Integer> f : results) {
            int s = f.get(60, TimeUnit.SECONDS);
            if (s == 201) {
                created++;
            } else if (s == 400) {
                rejected++;
            }
        }
        pool.shutdownNow();
        assertEquals(1, created, "đúng 1 đơn được tạo");
        assertEquals(n - 1, rejected, "các đơn còn lại bị từ chối 400 (hết hàng)");
        assertEquals(1, reserved(p.getProductId()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class));
    }

    // ------------------------------------------------------------------ BOLA / read

    @Test
    @DisplayName("BOLA: người ngoài cuộc nhận 404 ở mọi thao tác; buyer/seller xem được")
    void bola() throws Exception {
        Product p = newProduct(seller, category, "Bàn học", "400000", 2);
        long id = createOrder(buyer, p, "CASH_ON_MEETUP", "MEETUP");

        mockMvc.perform(auth(get("/api/v1/orders/" + id), outsider)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Không tìm thấy đơn hàng."));
        mockMvc.perform(auth(get("/api/v1/orders/" + id), buyer)).andExpect(status().isOk());
        mockMvc.perform(auth(get("/api/v1/orders/" + id), seller)).andExpect(status().isOk());
        mockMvc.perform(auth(get("/api/v1/orders/987654321"), buyer)).andExpect(status().isNotFound());
        cancel(outsider, id, "hack").andExpect(status().isNotFound());
        action(outsider, id, "confirm").andExpect(status().isNotFound());
        action(outsider, id, "complete").andExpect(status().isNotFound());
        action(outsider, id, "confirm-payment").andExpect(status().isNotFound());
        action(buyer, id, "deliver").andExpect(status().isNotFound());
        mockMvc.perform(auth(patch("/api/v1/orders/" + id + "/transaction-terms"), buyer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"payment_note\":\"x\"}")).andExpect(status().isNotFound());
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM orders WHERE order_id = ?", String.class, id));
    }

    @Test
    @DisplayName("Danh sách mua/bán: keyset cursor (created_at, order_id) DESC không trùng/sót, lọc status, cách ly theo user")
    void listWithKeysetCursor() throws Exception {
        Product p = newProduct(seller, category, "Bút bi", "5000", 20);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ids.add(createOrder(buyer, p, "CASH_ON_MEETUP", "MEETUP"));
        }
        action(seller, ids.get(0), "confirm").andExpect(status().isOk());
        createOrder(outsider, p, "CASH_ON_MEETUP", "MEETUP");   // đơn của người khác không lẫn vào

        Set<Long> seen = new HashSet<>();
        List<Long> order = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            var req = get("/api/v1/orders/me/buying").param("limit", "2");
            if (cursor != null) {
                req = req.param("cursor", cursor);
            }
            MvcResult r = mockMvc.perform(auth(req, buyer)).andExpect(status().isOk()).andReturn();
            String body = r.getResponse().getContentAsString();
            List<Number> pageIds = JsonPath.read(body, "$.items[*].order_id");
            for (Number n : pageIds) {
                assertTrue(seen.add(n.longValue()), "không được trùng đơn giữa các trang");
                order.add(n.longValue());
            }
            Boolean hasMore = JsonPath.read(body, "$.has_more");
            cursor = JsonPath.read(body, "$.next_cursor");
            assertEquals(hasMore, cursor != null);
            pages++;
        } while (cursor != null && pages < 10);
        assertEquals(3, pages);
        assertEquals(5, seen.size());
        List<Long> expectedDesc = new ArrayList<>(ids);
        java.util.Collections.reverse(expectedDesc);
        assertEquals(expectedDesc, order, "sắp xếp (created_at, order_id) DESC");

        mockMvc.perform(auth(get("/api/v1/orders/me/buying").param("status", "CONFIRMED"), buyer))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].order_id").value(ids.get(0)))
                .andExpect(jsonPath("$.items[0].item.product_title").value("Bút bi"))
                .andExpect(jsonPath("$.has_more").value(false))
                .andExpect(jsonPath("$.next_cursor").doesNotExist());
        mockMvc.perform(auth(get("/api/v1/orders/me/selling"), seller)).andExpect(jsonPath("$.items", hasSize(6)));
        mockMvc.perform(auth(get("/api/v1/orders/me/selling"), buyer)).andExpect(jsonPath("$.items", hasSize(0)));
        mockMvc.perform(auth(get("/api/v1/orders/me/buying").param("status", "NOPE"), buyer))
                .andExpect(jsonPath("$.items", hasSize(0)));
        mockMvc.perform(auth(get("/api/v1/orders/me/buying").param("limit", "0"), buyer)).andExpect(status().is4xxClientError());
        mockMvc.perform(auth(get("/api/v1/orders/me/buying").param("limit", "51"), buyer)).andExpect(status().is4xxClientError());
        // cursor hỏng bị bỏ qua giống Python → trang đầu
        mockMvc.perform(auth(get("/api/v1/orders/me/buying").param("cursor", "@@@"), buyer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(5)));
    }

    // ------------------------------------------------------------------ terms

    @Test
    @DisplayName("Cập nhật điều khoản: đổi MEETUP→SHIPPING cần địa chỉ, xoá meetup, thông báo khi có thay đổi thật; no-op không thông báo")
    void updateTransactionTerms() throws Exception {
        Product p = newProduct(seller, category, "Quạt", "120000", 4);
        long id = createOrder(buyer, p, "CASH_ON_MEETUP", "MEETUP");

        mockMvc.perform(auth(patch("/api/v1/orders/" + id + "/transaction-terms"), seller)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fulfillment_method\":\"SHIPPING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Vui lòng nhập địa chỉ giao hàng."));

        mockMvc.perform(auth(patch("/api/v1/orders/" + id + "/transaction-terms"), seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fulfillment_method\":\"SHIPPING\",\"shipping_address\":\"KTX B2 304\",\"payment_note\":\"ship nhanh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fulfillment_method").value("SHIPPING"))
                .andExpect(jsonPath("$.shipping_address").value("KTX B2 304"))
                .andExpect(jsonPath("$.meetup_place").doesNotExist())
                .andExpect(jsonPath("$.payment_note").value("ship nhanh"));
        assertNull(jdbc.queryForObject("SELECT meetup_place FROM orders WHERE order_id = ?", String.class, id));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type='ORDER_STATUS'",
                Integer.class, buyer.getUserId()));

        // no-op PATCH: không thông báo mới
        mockMvc.perform(auth(patch("/api/v1/orders/" + id + "/transaction-terms"), seller)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fulfillment_method\":\"SHIPPING\"}"))
                .andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type='ORDER_STATUS'",
                Integer.class, buyer.getUserId()));

        // chuyển sang BANK_TRANSFER khi seller chưa có bank → 400; có bank rồi → 200
        mockMvc.perform(auth(patch("/api/v1/orders/" + id + "/transaction-terms"), seller)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"payment_method\":\"BANK_TRANSFER\"}"))
                .andExpect(status().isBadRequest());
        giveSellerBank();
        mockMvc.perform(auth(patch("/api/v1/orders/" + id + "/transaction-terms"), seller)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"payment_method\":\"BANK_TRANSFER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payment_method").value("BANK_TRANSFER"));
        // đã CONFIRMED → snapshot ngay khi đổi sang bank; đổi lại tiền mặt → xoá snapshot
        action(seller, id, "confirm").andExpect(status().isOk());
        mockMvc.perform(auth(patch("/api/v1/orders/" + id + "/transaction-terms"), seller)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"payment_method\":\"CASH_ON_MEETUP\"}"))
                .andExpect(status().isOk());
        assertNull(jdbc.queryForObject("SELECT payment_bank_bin FROM orders WHERE order_id = ?", String.class, id));

        // đơn đã hoàn tất/huỷ không sửa được
        cancel(buyer, id, "thôi").andExpect(status().isOk());
        mockMvc.perform(auth(patch("/api/v1/orders/" + id + "/transaction-terms"), seller)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"payment_note\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ worker

    @Test
    @DisplayName("Worker auto-cancel: huỷ đơn PENDING quá hạn (hoàn kho, SYSTEM, thông báo cả 2 bên), bỏ qua đơn chưa hạn/đã xác nhận, idempotent")
    void autoCancelWorker() throws Exception {
        Product p = newProduct(seller, category, "Đồ cũ", "70000", 5);
        long expired = createOrder(buyer, p, "CASH_ON_MEETUP", "MEETUP");
        long fresh = createOrder(buyer, p, "CASH_ON_MEETUP", "MEETUP");
        long confirmed = createOrder(outsider, p, "CASH_ON_MEETUP", "MEETUP");
        action(seller, confirmed, "confirm").andExpect(status().isOk());
        assertEquals(3, reserved(p.getProductId()));

        expireOrderAutoCancel(expired);
        TrackATestConfig.USER_EVENTS.clear();

        SweepResult r = worker.runSweep();
        assertEquals(new SweepResult(1, 1, 0, 0), r);

        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM orders WHERE order_id = ?", String.class, expired));
        assertEquals("SYSTEM", jdbc.queryForObject("SELECT cancelled_by FROM orders WHERE order_id = ?", String.class, expired));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM orders WHERE order_id = ?", String.class, fresh));
        assertEquals("CONFIRMED", jdbc.queryForObject("SELECT status FROM orders WHERE order_id = ?", String.class, confirmed));
        assertEquals(2, reserved(p.getProductId()), "hoàn đúng 1 đơn vị");
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND ref_id = ? AND type='ORDER_STATUS'",
                Integer.class, buyer.getUserId(), expired));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND ref_id = ? AND type='ORDER_STATUS'",
                Integer.class, seller.getUserId(), expired));
        assertNull(jdbc.queryForObject("SELECT changed_by FROM order_status_history WHERE order_id = ? AND to_status='CANCELLED'",
                Long.class, expired));
        assertTrue(TrackATestConfig.USER_EVENTS.stream().anyMatch(e -> "AUTO_CANCELLED".equals(e.get("reason"))));

        // quét lại: không còn ứng viên, không sinh thông báo trùng
        assertEquals(SweepResult.empty(), worker.runSweep());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND ref_id = ? AND type='ORDER_STATUS'",
                Integer.class, buyer.getUserId(), expired));
    }

    @Test
    @DisplayName("Worker: đơn đã bị huỷ tay giữa lúc quét → no-op, không hoàn kho 2 lần")
    void autoCancelSingleIsIdempotent() throws Exception {
        Product p = newProduct(seller, category, "Tai nghe", "90000", 2);
        long id = createOrder(buyer, p, "CASH_ON_MEETUP", "MEETUP");
        expireOrderAutoCancel(id);
        cancel(buyer, id, "đổi ý").andExpect(status().isOk());
        assertEquals(0, reserved(p.getProductId()));
        assertEquals(SweepResult.empty(), worker.runSweep());   // CANCELLED không còn là ứng viên
        assertEquals(0, reserved(p.getProductId()));
    }
}
