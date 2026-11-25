package com.ptiters.marketplace.report;

import com.ptiters.marketplace.auction.Auction;
import com.ptiters.marketplace.auction.AuctionStatus;
import com.ptiters.marketplace.category.Category;
import com.ptiters.marketplace.order.Order;
import com.ptiters.marketplace.order.OrderStatus;
import com.ptiters.marketplace.product.Product;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TrackCTestConfig.class)
class ReportModerationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TrackCFixtures fx;
    @Autowired JdbcTemplate jdbc;

    User admin;
    User reporter;
    User target;

    @BeforeEach
    void setUp() {
        fx.cleanAll();
        TrackCTestConfig.reset();
        admin = fx.user("admin", UserRole.ADMIN);
        reporter = fx.user("reporter", UserRole.STUDENT);
        target = fx.user("target", UserRole.STUDENT);
    }

    @AfterEach
    void tearDown() {
        fx.cleanAll();
        for (String t : new String[]{"reports", "moderation_actions", "user_capability_restrictions", "notifications", "users", "auctions", "products"}) {
            assertEquals(0, fx.count(t), t);
        }
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, User u) {
        return b.header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token(u)).contentType(MediaType.APPLICATION_JSON);
    }

    private long newReport(String targetType, long targetId, String reason, User by) {
        jdbc.update("INSERT INTO reports(reporter_id, target_type, target_id, reason, status) VALUES (?,?,?,?, 'OPEN')",
                by.getUserId(), targetType, targetId, reason);
        return jdbc.queryForObject("SELECT max(report_id) FROM reports", Long.class);
    }

    private ResultActions enforce(long reportId, String body) throws Exception {
        return mockMvc.perform(as(post("/api/v1/admin/reports/" + reportId + "/enforcement"), admin).content(body));
    }

    private String userStatus(User u) {
        return jdbc.queryForObject("SELECT status::text FROM users WHERE user_id = ?", String.class, u.getUserId());
    }

    private int actions(long reportId, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM moderation_actions WHERE report_id = ? AND action_type = ?",
                Integer.class, reportId, type);
    }

    @Test
    @DisplayName("Chỉ ADMIN: STUDENT 403, không token 401 trên mọi endpoint admin báo cáo")
    void adminOnly() throws Exception {
        long id = newReport("USER", target.getUserId(), "SCAM", reporter);
        for (MockHttpServletRequestBuilder b : new MockHttpServletRequestBuilder[]{
                get("/api/v1/admin/reports"), get("/api/v1/admin/reports/" + id),
                patch("/api/v1/admin/reports/" + id + "/status").content("{\"status\":\"REVIEWING\"}"),
                post("/api/v1/admin/reports/" + id + "/enforcement").content("{\"action\":\"USER_BAN\"}"),
                get("/api/v1/admin/users/" + target.getUserId() + "/report-history")}) {
            mockMvc.perform(as(b, reporter)).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/admin/reports")).andExpect(status().isUnauthorized());
        assertEquals("ACTIVE", userStatus(target));
    }

    @Test
    @DisplayName("Hàng đợi: lọc queue_state/workflow/conclusion/target_type, kết hợp sai 422, phân trang cursor")
    void queue() throws Exception {
        long a = newReport("USER", target.getUserId(), "SCAM", reporter);
        long b = newReport("USER", admin.getUserId(), "SPAM", reporter);
        jdbc.update("UPDATE reports SET status = 'RESOLVED' WHERE report_id = ?", b);

        mockMvc.perform(as(get("/api/v1/admin/reports").param("queue_state", "NEEDS_ACTION"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].report_id").value(a))
                .andExpect(jsonPath("$.items[0].workflow_state").value("OPEN"))
                .andExpect(jsonPath("$.items[0].conclusion").value(nullValue()))
                .andExpect(jsonPath("$.items[0].target_available").value(true))
                .andExpect(jsonPath("$.items[0].target_summary.user_id").value(target.getUserId()))
                .andExpect(jsonPath("$.items[0].reporter.user_id").value(reporter.getUserId()));
        mockMvc.perform(as(get("/api/v1/admin/reports").param("workflow", "COMPLETED").param("conclusion", "SUPPORTED"), admin))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].report_id").value(b))
                .andExpect(jsonPath("$.items[0].conclusion").value("SUPPORTED"));
        mockMvc.perform(as(get("/api/v1/admin/reports").param("target_type", "PRODUCT"), admin))
                .andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(as(get("/api/v1/admin/reports").param("queue_state", "COMPLETED").param("status", "OPEN"), admin))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(as(get("/api/v1/admin/reports").param("workflow", "OPEN").param("conclusion", "SUPPORTED"), admin))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(as(get("/api/v1/admin/reports").param("limit", "100"), admin)).andExpect(status().isUnprocessableEntity());

        var page = mockMvc.perform(as(get("/api/v1/admin/reports").param("limit", "1"), admin))
                .andExpect(jsonPath("$.items[0].report_id").value(b))
                .andExpect(jsonPath("$.has_more").value(true)).andReturn();
        String cursor = com.jayway.jsonpath.JsonPath.read(page.getResponse().getContentAsString(), "$.next_cursor");
        mockMvc.perform(as(get("/api/v1/admin/reports").param("limit", "1").param("cursor", cursor), admin))
                .andExpect(jsonPath("$.items[0].report_id").value(a))
                .andExpect(jsonPath("$.has_more").value(false));
        mockMvc.perform(as(get("/api/v1/admin/reports").param("cursor", "!!bad!!"), admin)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Workflow: OPEN→REVIEWING→RESOLVED, thông báo cho người báo cáo, trạng thái kết thúc 409, chuyển sai 409")
    void workflow() throws Exception {
        long id = newReport("USER", target.getUserId(), "SCAM", reporter);
        String url = "/api/v1/admin/reports/" + id + "/status";

        mockMvc.perform(as(patch(url), admin).content("{\"status\":\"OPEN\"}")).andExpect(status().isConflict());
        mockMvc.perform(as(patch(url), admin).content("{\"status\":\"REVIEWING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEWING"))
                .andExpect(jsonPath("$.handled_by").value(admin.getUserId()))
                .andExpect(jsonPath("$.handled_at").value(nullValue()))
                .andExpect(jsonPath("$.available_moderation_actions", contains("CONFIRM_SUPPORTED", "MARK_INSUFFICIENT_EVIDENCE")));
        assertEquals(0, fx.count("notifications"));

        mockMvc.perform(as(patch(url), admin).content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflow_state").value("COMPLETED"))
                .andExpect(jsonPath("$.conclusion").value("INSUFFICIENT_EVIDENCE"))
                .andExpect(jsonPath("$.handled_at").isNotEmpty());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE user_id = ? AND ref_type = 'REPORT' AND ref_id = ?",
                Integer.class, reporter.getUserId(), id));
        mockMvc.perform(as(patch(url), admin).content("{\"status\":\"RESOLVED\"}")).andExpect(status().isConflict());
        mockMvc.perform(as(patch(url), admin).content("{\"status\":\"BOGUS\"}")).andExpect(status().isUnprocessableEntity());
        mockMvc.perform(as(patch("/api/v1/admin/reports/999999/status"), admin).content("{\"status\":\"REVIEWING\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("USER_BAN: BANNED + RESOLVED + ModerationAction + thu hồi phiên SAU commit; ban lại 409; ban ADMIN 403; UNBAN đúng nguồn")
    void banAndUnban() throws Exception {
        long id = newReport("USER", target.getUserId(), "SCAM", reporter);
        long other = newReport("USER", target.getUserId(), "SPAM", reporter);

        enforce(id, "{\"action\":\"USER_BAN\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.available_enforcement_actions", contains("USER_UNBAN")))
                .andExpect(jsonPath("$.enforcement_actions[0].action_type").value("USER_BAN"))
                .andExpect(jsonPath("$.enforcement_actions[0].previous_status").value("ACTIVE"))
                .andExpect(jsonPath("$.enforcement_actions[0].new_status").value("BANNED"))
                .andExpect(jsonPath("$.reported_subject.status").value("BANNED"))
                .andExpect(jsonPath("$.reported_subject.can_buy").value(false));
        assertEquals("BANNED", userStatus(target));
        assertTrue(TrackCTestConfig.REVOKED_USERS.contains(target.getUserId()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND ref_type = 'REPORT'",
                Integer.class, reporter.getUserId()));

        enforce(other, "{\"action\":\"USER_BAN\"}").andExpect(status().isConflict());
        // UNBAN từ báo cáo KHÔNG phải nguồn ban → 409
        enforce(other, "{\"action\":\"USER_UNBAN\"}").andExpect(status().isConflict());
        enforce(id, "{\"action\":\"USER_UNBAN\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.handled_by").value(admin.getUserId()));
        assertEquals("ACTIVE", userStatus(target));
        assertEquals(1, actions(id, "USER_UNBAN"));
        enforce(id, "{\"action\":\"USER_UNBAN\"}").andExpect(status().isConflict());

        long adminReport = newReport("USER", admin.getUserId(), "SCAM", reporter);
        enforce(adminReport, "{\"action\":\"USER_BAN\"}").andExpect(status().isForbidden());
        enforce(id, "{\"action\":\"NOPE\"}").andExpect(status().isUnprocessableEntity());
        enforce(id, "{\"action\":\"USER_BAN\",\"duration_days\":3}").andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("USER_BAN trên báo cáo ORDER: chế tài đối tác của người báo cáo trong đơn")
    void banViaOrderReport() throws Exception {
        Order order = fx.order(reporter, target, OrderStatus.PENDING);
        long id = newReport("ORDER", order.getOrderId(), "NO_SHOW", reporter);
        mockMvc.perform(as(get("/api/v1/admin/reports/" + id), admin))
                .andExpect(jsonPath("$.reported_subject.user_id").value(target.getUserId()))
                .andExpect(jsonPath("$.reported_subject.role_in_order").value("SELLER"))
                .andExpect(jsonPath("$.target_summary.order_code").value(order.getOrderCode()));
        enforce(id, "{\"action\":\"USER_BAN\"}").andExpect(status().isOk());
        assertEquals("BANNED", userStatus(target));
        assertEquals("ACTIVE", userStatus(reporter));
    }

    @Test
    @DisplayName("PRODUCT_HIDE: BLOCKED, huỷ đấu giá + về FIXED_PRICE + publish AUCTION_CANCELLED sau commit; RESTORE đúng nguồn")
    void hideAndRestoreProduct() throws Exception {
        Category c = fx.category();
        Product p = fx.product(target, c, "Hàng vi phạm");
        Auction a = fx.auction(p, AuctionStatus.ACTIVE);
        jdbc.update("UPDATE products SET listing_type = 'AUCTION' WHERE product_id = ?", p.getProductId());
        long id = newReport("PRODUCT", p.getProductId(), "FAKE_ITEM", reporter);
        long second = newReport("PRODUCT", p.getProductId(), "SPAM", reporter);

        enforce(id, "{\"action\":\"PRODUCT_HIDE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.target_summary.moderation_status").value("BLOCKED"))
                .andExpect(jsonPath("$.available_enforcement_actions", contains("PRODUCT_RESTORE")));
        assertEquals("BLOCKED", jdbc.queryForObject("SELECT moderation_status FROM products WHERE product_id = ?", String.class, p.getProductId()));
        assertEquals("FIXED_PRICE", jdbc.queryForObject("SELECT listing_type FROM products WHERE product_id = ?", String.class, p.getProductId()));
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM auctions WHERE auction_id = ?", String.class, a.getAuctionId()));
        assertEquals(java.util.List.of(a.getAuctionId()), TrackCTestConfig.CANCELLED_AUCTIONS);
        assertEquals(1, actions(id, "PRODUCT_HIDE"));

        enforce(second, "{\"action\":\"PRODUCT_HIDE\"}").andExpect(status().isConflict());
        enforce(second, "{\"action\":\"PRODUCT_RESTORE\"}").andExpect(status().isConflict());
        enforce(id, "{\"action\":\"PRODUCT_RESTORE\"}").andExpect(status().isOk());
        assertEquals("CLEAR", jdbc.queryForObject("SELECT moderation_status FROM products WHERE product_id = ?", String.class, p.getProductId()));
        assertEquals(1, actions(id, "PRODUCT_RESTORE"));
        // Chế tài sản phẩm trên báo cáo USER → 400
        long userReport = newReport("USER", target.getUserId(), "SCAM", reporter);
        enforce(userReport, "{\"action\":\"PRODUCT_HIDE\"}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CAPABILITY_RESTRICT BUY/SELL: bản ghi hạn chế + BUY/SELL_RESTRICT + huỷ đấu giá; trùng 409; RESTORE đúng nguồn; access-status phản ánh")
    void capabilityRestrictAndRestore() throws Exception {
        Category c = fx.category();
        Product p = fx.product(target, c, "Hàng của target");
        Auction a = fx.auction(p, AuctionStatus.SCHEDULED);
        long id = newReport("USER", target.getUserId(), "SCAM", reporter);

        enforce(id, "{\"action\":\"CAPABILITY_RESTRICT\",\"capabilities\":[\"BUY\"]}").andExpect(status().isUnprocessableEntity());
        enforce(id, "{\"action\":\"CAPABILITY_RESTRICT\",\"capabilities\":[\"BUY\"],\"duration_days\":5}").andExpect(status().isUnprocessableEntity());
        enforce(id, "{\"action\":\"CAPABILITY_RESTRICT\",\"capabilities\":[\"FLY\"],\"duration_days\":7}").andExpect(status().isUnprocessableEntity());

        enforce(id, "{\"action\":\"CAPABILITY_RESTRICT\",\"capabilities\":[\"BUY\",\"SELL\"],\"duration_days\":7}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.reported_subject.can_buy").value(false))
                .andExpect(jsonPath("$.reported_subject.can_sell").value(false))
                .andExpect(jsonPath("$.reported_subject.active_restrictions.length()").value(2))
                .andExpect(jsonPath("$.available_enforcement_actions", contains("CAPABILITY_RESTORE")));
        assertEquals(2, fx.count("user_capability_restrictions"));
        assertEquals(1, actions(id, "BUY_RESTRICT"));
        assertEquals(1, actions(id, "SELL_RESTRICT"));
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM auctions WHERE auction_id = ?", String.class, a.getAuctionId()));
        assertTrue(TrackCTestConfig.CANCELLED_AUCTIONS.contains(a.getAuctionId()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND ref_type = 'ACCOUNT_ACCESS'",
                Integer.class, target.getUserId()));

        mockMvc.perform(as(get("/api/v1/users/me/access-status"), target))
                .andExpect(jsonPath("$.can_buy").value(false)).andExpect(jsonPath("$.can_sell").value(false));

        long other = newReport("USER", target.getUserId(), "SPAM", reporter);
        enforce(other, "{\"action\":\"CAPABILITY_RESTRICT\",\"capabilities\":[\"BUY\"],\"duration_days\":1}").andExpect(status().isConflict());
        // Khôi phục từ báo cáo không phải nguồn → 409
        enforce(other, "{\"action\":\"CAPABILITY_RESTORE\",\"capabilities\":[\"BUY\"]}").andExpect(status().isConflict());
        enforce(id, "{\"action\":\"CAPABILITY_RESTORE\",\"capabilities\":[\"BUY\"],\"duration_days\":1}").andExpect(status().isUnprocessableEntity());

        enforce(id, "{\"action\":\"CAPABILITY_RESTORE\",\"capabilities\":[\"BUY\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reported_subject.can_buy").value(true))
                .andExpect(jsonPath("$.reported_subject.can_sell").value(false));
        assertEquals(1, actions(id, "BUY_RESTORE"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM user_capability_restrictions WHERE revoked_at IS NOT NULL AND revoked_by = ?",
                Integer.class, admin.getUserId()));
        mockMvc.perform(as(get("/api/v1/users/me/access-status"), target))
                .andExpect(jsonPath("$.can_buy").value(true)).andExpect(jsonPath("$.can_sell").value(false));
    }

    @Test
    @DisplayName("Lịch sử báo cáo theo đối tượng: RECEIVED / SENT, loại trừ báo cáo hiện tại, phân trang")
    void subjectHistory() throws Exception {
        long r1 = newReport("USER", target.getUserId(), "SCAM", reporter);
        long r2 = newReport("USER", target.getUserId(), "SPAM", reporter);
        newReport("USER", reporter.getUserId(), "SPAM", target);
        String url = "/api/v1/admin/users/" + target.getUserId() + "/report-history";

        mockMvc.perform(as(get(url), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_count").value(2))
                .andExpect(jsonPath("$.items[0].report_id").value(r2))
                .andExpect(jsonPath("$.has_more").value(false));
        mockMvc.perform(as(get(url).param("exclude_report_id", String.valueOf(r2)), admin))
                .andExpect(jsonPath("$.total_count").value(1))
                .andExpect(jsonPath("$.items[0].report_id").value(r1));
        mockMvc.perform(as(get(url).param("type", "SENT"), admin))
                .andExpect(jsonPath("$.total_count").value(1));
        mockMvc.perform(as(get(url).param("limit", "1"), admin))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.has_more").value(true))
                .andExpect(jsonPath("$.next_cursor").isNotEmpty());
        mockMvc.perform(as(get("/api/v1/admin/users/999999999/report-history"), admin)).andExpect(status().isNotFound());
        mockMvc.perform(as(get(url).param("type", "XX"), admin)).andExpect(status().isUnprocessableEntity());
    }
}
