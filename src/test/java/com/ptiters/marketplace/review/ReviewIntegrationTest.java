package com.ptiters.marketplace.review;

import com.ptiters.marketplace.order.Order;
import com.ptiters.marketplace.order.OrderStatus;
import com.ptiters.marketplace.trackc.TrackCFixtures;
import com.ptiters.marketplace.trackc.TrackCTestConfig;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TrackCTestConfig.class)
class ReviewIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TrackCFixtures fx;
    @Autowired JdbcTemplate jdbc;

    User buyer;
    User seller;
    User stranger;

    @BeforeEach
    void setUp() {
        fx.cleanAll();
        TrackCTestConfig.reset();
        buyer = fx.user("buyer", UserRole.STUDENT);
        seller = fx.user("seller", UserRole.STUDENT);
        stranger = fx.user("stranger", UserRole.STUDENT);
    }

    @AfterEach
    void tearDown() {
        fx.cleanAll();
        assertEquals(0, fx.count("reviews"));
        assertEquals(0, fx.count("orders"));
        assertEquals(0, fx.count("notifications"));
        assertEquals(0, fx.count("users"));
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, User u) {
        return b.header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token(u)).contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("POST review: người mua đánh giá đơn COMPLETED → 201 + notification NEW_REVIEW cho người bán")
    void createReview_success() throws Exception {
        Order order = fx.order(buyer, seller, OrderStatus.COMPLETED);

        mockMvc.perform(auth(post("/api/v1/orders/" + order.getOrderId() + "/review"), buyer)
                        .content("{\"rating\":5,\"comment\":\"  Giao dịch nhanh  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order_id").value(order.getOrderId()))
                .andExpect(jsonPath("$.reviewer_id").value(buyer.getUserId()))
                .andExpect(jsonPath("$.reviewee_id").value(seller.getUserId()))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Giao dịch nhanh"))
                .andExpect(jsonPath("$.reviewer_name").value(buyer.getFullName()))
                .andExpect(jsonPath("$.created_at").exists());

        assertEquals(1, fx.count("reviews"));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'NEW_REVIEW' AND ref_type = 'ORDER' AND ref_id = ?",
                Integer.class, seller.getUserId(), order.getOrderId()));
        String body = jdbc.queryForObject("SELECT body FROM notifications WHERE user_id = ?", String.class, seller.getUserId());
        assertEquals("Đơn " + order.getOrderCode() + " vừa nhận được đánh giá 5 sao.", body);
        // Realtime NOTIFICATION_CREATED được dispatch SAU commit
        assertEquals(1, TrackCTestConfig.USER_EVENTS.size());
        assertEquals(seller.getUserId(), TrackCTestConfig.USER_EVENTS.get(0).get("userId"));
    }

    @Test
    @DisplayName("POST review: trùng đánh giá → 409 và không sinh thêm notification")
    void createReview_duplicate() throws Exception {
        Order order = fx.order(buyer, seller, OrderStatus.COMPLETED);
        String url = "/api/v1/orders/" + order.getOrderId() + "/review";
        mockMvc.perform(auth(post(url), buyer).content("{\"rating\":4}")).andExpect(status().isCreated());
        mockMvc.perform(auth(post(url), buyer).content("{\"rating\":3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Bạn đã đánh giá đơn hàng này."));
        assertEquals(1, fx.count("reviews"));
        assertEquals(1, fx.count("notifications"));
    }

    @Test
    @DisplayName("POST review: đơn chưa COMPLETED → 400, không có review/notification")
    void createReview_orderNotCompleted() throws Exception {
        Order order = fx.order(buyer, seller, OrderStatus.PENDING);
        mockMvc.perform(auth(post("/api/v1/orders/" + order.getOrderId() + "/review"), buyer).content("{\"rating\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Chỉ có thể đánh giá sau khi đơn hàng hoàn tất."));
        assertEquals(0, fx.count("reviews"));
        assertEquals(0, fx.count("notifications"));
    }

    @Test
    @DisplayName("POST review: người bán không được đánh giá (403); người ngoài đơn (404)")
    void createReview_wrongActor() throws Exception {
        Order order = fx.order(buyer, seller, OrderStatus.COMPLETED);
        String url = "/api/v1/orders/" + order.getOrderId() + "/review";
        mockMvc.perform(auth(post(url), seller).content("{\"rating\":5}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Chỉ người mua mới có thể đánh giá người bán."));
        mockMvc.perform(auth(post(url), stranger).content("{\"rating\":5}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Không tìm thấy đơn hàng."));
        assertEquals(0, fx.count("reviews"));
    }

    @Test
    @DisplayName("Tự đánh giá: DB chặn cả đơn buyer == seller lẫn review reviewer == reviewee (CHECK thật trên PostgreSQL)")
    void selfReview_blockedByDatabaseChecks() {
        // Đơn có buyer == seller không thể tồn tại → service không bao giờ gặp ca này (guard 400 chỉ là lớp phòng thủ).
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> fx.order(buyer, buyer, OrderStatus.COMPLETED));
        Order order = fx.order(buyer, seller, OrderStatus.COMPLETED);
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO reviews(order_id, reviewer_id, reviewee_id, rating) VALUES (?,?,?,5)",
                        order.getOrderId(), buyer.getUserId(), buyer.getUserId()));
        assertEquals(0, fx.count("reviews"));
    }

    @Test
    @DisplayName("POST review: payload sai (rating ngoài 1..5, thiếu rating, trường thừa, comment > 1000) → 422")
    void createReview_invalidPayload() throws Exception {
        Order order = fx.order(buyer, seller, OrderStatus.COMPLETED);
        String url = "/api/v1/orders/" + order.getOrderId() + "/review";
        mockMvc.perform(auth(post(url), buyer).content("{\"rating\":6}")).andExpect(status().isUnprocessableEntity());
        mockMvc.perform(auth(post(url), buyer).content("{\"rating\":0}")).andExpect(status().isUnprocessableEntity());
        mockMvc.perform(auth(post(url), buyer).content("{\"comment\":\"x\"}")).andExpect(status().isUnprocessableEntity());
        mockMvc.perform(auth(post(url), buyer).content("{\"rating\":5,\"foo\":1}")).andExpect(status().isUnprocessableEntity());
        mockMvc.perform(auth(post(url), buyer).content("{\"rating\":5,\"comment\":\"" + "a".repeat(1001) + "\"}"))
                .andExpect(status().isUnprocessableEntity());
        assertEquals(0, fx.count("reviews"));
    }

    @Test
    @DisplayName("POST review: comment toàn khoảng trắng chuẩn hoá về null")
    void createReview_blankCommentBecomesNull() throws Exception {
        Order order = fx.order(buyer, seller, OrderStatus.COMPLETED);
        mockMvc.perform(auth(post("/api/v1/orders/" + order.getOrderId() + "/review"), buyer)
                        .content("{\"rating\":3,\"comment\":\"    \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comment").value(nullValue()));
    }

    @Test
    @DisplayName("GET review: người tham gia đơn nhận null khi chưa có, sau khi có thì nhận review; người ngoài 404")
    void getReview() throws Exception {
        Order order = fx.order(buyer, seller, OrderStatus.COMPLETED);
        String url = "/api/v1/orders/" + order.getOrderId() + "/review";
        mockMvc.perform(auth(get(url), seller)).andExpect(status().isOk()).andExpect(content().string("null"));
        mockMvc.perform(auth(post(url), buyer).content("{\"rating\":4,\"comment\":\"ok\"}")).andExpect(status().isCreated());
        mockMvc.perform(auth(get(url), seller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.reviewer_name").value(buyer.getFullName()));
        mockMvc.perform(auth(get(url), stranger)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH review: sửa rating/comment, body rỗng và rating null → 422, comment null xoá nhận xét, không tạo notification mới")
    void updateReview() throws Exception {
        Order order = fx.order(buyer, seller, OrderStatus.COMPLETED);
        String url = "/api/v1/orders/" + order.getOrderId() + "/review";
        mockMvc.perform(auth(post(url), buyer).content("{\"rating\":5,\"comment\":\"tốt\"}")).andExpect(status().isCreated());

        mockMvc.perform(auth(patch(url), buyer).content("{\"rating\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(2))
                .andExpect(jsonPath("$.comment").value("tốt"));
        mockMvc.perform(auth(patch(url), buyer).content("{\"comment\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(2))
                .andExpect(jsonPath("$.comment").value(nullValue()));
        mockMvc.perform(auth(patch(url), buyer).content("{}")).andExpect(status().isUnprocessableEntity());
        mockMvc.perform(auth(patch(url), buyer).content("{\"rating\":null}")).andExpect(status().isUnprocessableEntity());
        // Người bán / người ngoài không có review của riêng mình → 404
        mockMvc.perform(auth(patch(url), seller).content("{\"rating\":1}")).andExpect(status().isNotFound());
        assertEquals(1, fx.count("notifications"));
    }

    @Test
    @DisplayName("GET /users/{public_id}/reviews: công khai (không token), ẩn danh, keyset cursor, ACTIVE-only")
    void publicSellerReviews() throws Exception {
        for (int i = 1; i <= 3; i++) {
            Order o = fx.order(buyer, seller, OrderStatus.COMPLETED);
            // Mỗi review do một đơn khác nhau; reviewer là buyer
            jdbc.update("INSERT INTO reviews(order_id, reviewer_id, reviewee_id, rating, comment, created_at) VALUES (?,?,?,?,?, now() - (? || ' minutes')::interval)",
                    o.getOrderId(), buyer.getUserId(), seller.getUserId(), i, "nhận xét " + i, i);
        }
        String url = "/api/v1/users/" + seller.getPublicId() + "/reviews";

        var first = mockMvc.perform(get(url).param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].rating").value(1))
                .andExpect(jsonPath("$.items[1].rating").value(2))
                .andExpect(jsonPath("$.items[0].verified_transaction").value(true))
                .andExpect(jsonPath("$.items[0].reviewer_name").doesNotExist())
                .andExpect(jsonPath("$.items[0].order_id").doesNotExist())
                .andExpect(jsonPath("$.items[0].reviewer_id").doesNotExist())
                .andExpect(jsonPath("$.has_more").value(true))
                .andExpect(jsonPath("$.next_cursor").isNotEmpty())
                .andReturn();
        String cursor = com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.next_cursor");

        mockMvc.perform(get(url).param("limit", "2").param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].rating").value(3))
                .andExpect(jsonPath("$.has_more").value(false))
                .andExpect(jsonPath("$.next_cursor").value(nullValue()));

        mockMvc.perform(get(url).param("limit", "0")).andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get(url).param("cursor", "***not-base64***")).andExpect(status().isUnprocessableEntity());

        // Người bán bị khóa/không tồn tại → 404
        jdbc.update("UPDATE users SET status = 'BANNED' WHERE user_id = ?", seller.getUserId());
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users/" + java.util.UUID.randomUUID() + "/reviews")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Endpoint review theo đơn không token → 401; route công khai reviews không cần token")
    void unauthenticated() throws Exception {
        Order order = fx.order(buyer, seller, OrderStatus.COMPLETED);
        mockMvc.perform(post("/api/v1/orders/" + order.getOrderId() + "/review")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/orders/" + order.getOrderId() + "/review")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/" + seller.getPublicId() + "/reviews")).andExpect(status().isOk());
    }
}
