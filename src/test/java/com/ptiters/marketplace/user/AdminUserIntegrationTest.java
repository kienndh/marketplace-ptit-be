package com.ptiters.marketplace.user;

import com.ptiters.marketplace.trackc.TrackCFixtures;
import com.ptiters.marketplace.trackc.TrackCTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TrackCTestConfig.class)
class AdminUserIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TrackCFixtures fx;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserSearchService userSearchService;

    User admin;
    User alice;
    User bob;

    @BeforeEach
    void setUp() {
        fx.cleanAll();
        admin = fx.user("admin", UserRole.ADMIN);
        alice = fx.user("alice", UserRole.STUDENT);
        bob = fx.user("bob", UserRole.STUDENT);
    }

    @AfterEach
    void tearDown() {
        fx.cleanAll();
        assertEquals(0, fx.count("users"));
        assertEquals(0, fx.count("reports"));
        assertEquals(0, fx.count("user_capability_restrictions"));
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder b) {
        return b.header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token(admin));
    }

    @Autowired UserCapabilityRestrictionRepository restrictionRepository;

    /** Tạo qua entity (cùng đường Hibernate với code thật) để thời gian nhất quán với truy vấn của service. */
    private void restrict(User u, long reportId, String cap) {
        UserCapabilityRestriction r = new UserCapabilityRestriction();
        r.setUserId(u.getUserId());
        r.setSourceReportId(reportId);
        r.setImposedBy(admin.getUserId());
        r.setCapability(cap);
        r.setStartsAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(1));
        r.setExpiresAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusDays(1));
        restrictionRepository.saveAndFlush(r);
    }

    private long report(String type, long targetId, User by, String status) {
        jdbc.update("INSERT INTO reports(reporter_id, target_type, target_id, reason, status) VALUES (?,?,?,'SCAM',?)",
                by.getUserId(), type, targetId, status);
        return jdbc.queryForObject("SELECT max(report_id) FROM reports", Long.class);
    }

    @Test
    @DisplayName("Admin users: chỉ ADMIN (STUDENT 403, không token 401)")
    void adminOnly() throws Exception {
        for (String url : new String[]{"/api/v1/admin/users", "/api/v1/admin/users/" + alice.getUserId(),
                "/api/v1/admin/users/" + alice.getUserId() + "/moderation-history"}) {
            mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token(alice))).andExpect(status().isForbidden());
            mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("Danh sách: stats, lọc status/role/capability_restriction, tìm theo tên/email/id/public_id, phân trang")
    void list() throws Exception {
        long rep = report("USER", alice.getUserId(), bob, "RESOLVED");
        restrict(alice, rep, "BUY");
        jdbc.update("UPDATE users SET status = 'BANNED' WHERE user_id = ?", bob.getUserId());

        mockMvc.perform(asAdmin(get("/api/v1/admin/users")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.stats.total_users").value(3))
                .andExpect(jsonPath("$.stats.active_users").value(2))
                .andExpect(jsonPath("$.stats.banned_users").value(1))
                .andExpect(jsonPath("$.stats.restricted_users").value(1))
                .andExpect(jsonPath("$.total_pages").value(1));
        mockMvc.perform(asAdmin(get("/api/v1/admin/users").param("capability_restriction", "BUY")))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].user_id").value(alice.getUserId()))
                .andExpect(jsonPath("$.items[0].can_buy").value(false))
                .andExpect(jsonPath("$.items[0].can_sell").value(true))
                .andExpect(jsonPath("$.items[0].active_restrictions", contains("BUY")))
                .andExpect(jsonPath("$.items[0].report_count").value(1));
        mockMvc.perform(asAdmin(get("/api/v1/admin/users").param("status", "BANNED")))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].can_buy").value(false));
        mockMvc.perform(asAdmin(get("/api/v1/admin/users").param("role", "ADMIN")))
                .andExpect(jsonPath("$.items.length()").value(1));
        mockMvc.perform(asAdmin(get("/api/v1/admin/users").param("search", "ALICE")))
                .andExpect(jsonPath("$.items.length()").value(1));
        mockMvc.perform(asAdmin(get("/api/v1/admin/users").param("search", String.valueOf(bob.getUserId()))))
                .andExpect(jsonPath("$.items[0].user_id").value(bob.getUserId()));
        mockMvc.perform(asAdmin(get("/api/v1/admin/users").param("search", alice.getPublicId().toString())))
                .andExpect(jsonPath("$.items.length()").value(1));
        mockMvc.perform(asAdmin(get("/api/v1/admin/users").param("page_size", "2").param("page", "2")))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.total_pages").value(2));
        mockMvc.perform(asAdmin(get("/api/v1/admin/users").param("status", "WEIRD"))).andExpect(status().isUnprocessableEntity());
        mockMvc.perform(asAdmin(get("/api/v1/admin/users").param("page_size", "101"))).andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Chi tiết + lịch sử chế tài: số báo cáo, eligible_reports, all/active restrictions, moderation-history, 404")
    void detailAndHistory() throws Exception {
        long open = report("USER", alice.getUserId(), bob, "OPEN");
        long done = report("USER", alice.getUserId(), bob, "RESOLVED");
        report("USER", bob.getUserId(), alice, "OPEN");
        restrict(alice, done, "SELL");
        jdbc.update("INSERT INTO moderation_actions(report_id, admin_id, target_type, target_id, action_type, previous_status, new_status) VALUES (?,?, 'USER', ?, 'SELL_RESTRICT', 'UNRESTRICTED', 'RESTRICTED')",
                done, admin.getUserId(), alice.getUserId());

        mockMvc.perform(asAdmin(get("/api/v1/admin/users/" + alice.getUserId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(alice.getEmail()))
                .andExpect(jsonPath("$.can_buy").value(true))
                .andExpect(jsonPath("$.can_sell").value(false))
                .andExpect(jsonPath("$.total_received_reports").value(2))
                .andExpect(jsonPath("$.supported_received_reports").value(1))
                .andExpect(jsonPath("$.total_sent_reports").value(1))
                .andExpect(jsonPath("$.active_restrictions.length()").value(1))
                .andExpect(jsonPath("$.active_restrictions[0].imposed_by_name").value(admin.getFullName()))
                .andExpect(jsonPath("$.all_restrictions[0].is_active").value(true))
                .andExpect(jsonPath("$.eligible_reports.length()").value(1))
                .andExpect(jsonPath("$.eligible_reports[0].report_id").value(open));

        mockMvc.perform(asAdmin(get("/api/v1/admin/users/" + alice.getUserId() + "/moderation-history")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].action_type").value("SELL_RESTRICT"))
                .andExpect(jsonPath("$[0].admin_email").value(admin.getEmail()))
                .andExpect(jsonPath("$[0].report_id").value(done));

        mockMvc.perform(asAdmin(get("/api/v1/admin/users/999999999"))).andExpect(status().isNotFound());
        mockMvc.perform(asAdmin(get("/api/v1/admin/users/999999999/moderation-history"))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("UserSearchService: chỉ user ACTIVE, loại chính mình, không lộ email, tối thiểu 2 ký tự")
    void search() {
        jdbc.update("UPDATE users SET full_name = 'Nguyen Van Tim' WHERE user_id = ?", alice.getUserId());
        jdbc.update("UPDATE users SET full_name = 'Nguyen Thi Tam', status = 'BANNED' WHERE user_id = ?", bob.getUserId());
        List<UserSearchResponse> r = userSearchService.search(admin.getUserId(), "nguyen", 10, null);
        assertEquals(1, r.size());
        assertEquals(alice.getUserId(), r.get(0).userId());
        assertEquals(0, userSearchService.search(admin.getUserId(), "n", 10, null).size());
        assertEquals(0, userSearchService.search(alice.getUserId(), "Nguyen Van", 10, null).size());
    }

    @Test
    @DisplayName("GET /users/search: q>=2, limit 1..50, loại chính mình, conversation_id loại thành viên nhóm")
    void searchEndpoint() throws Exception {
        jdbc.update("UPDATE users SET full_name = 'Tran Van Hung' WHERE user_id = ?", alice.getUserId());
        jdbc.update("UPDATE users SET full_name = 'Tran Thi Hoa' WHERE user_id = ?", bob.getUserId());
        jdbc.update("UPDATE users SET full_name = 'Tran Admin' WHERE user_id = ?", admin.getUserId());

        mockMvc.perform(get("/api/v1/users/search").param("q", "tran")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].email").doesNotExist());
        // q ngắn hơn 2 ký tự / limit ngoài [1,50] → 422 như FastAPI
        mockMvc.perform(get("/api/v1/users/search").param("q", "t")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token(admin)))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/v1/users/search").param("q", "tran").param("limit", "51")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token(admin)))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/v1/users/search").param("q", "tran").param("limit", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/v1/users/search").param("q", "tran"))
                .andExpect(status().isUnauthorized());

        Long convId = jdbc.queryForObject(
                "INSERT INTO conversations (type, title, requires_approval, created_at) VALUES ('GROUP','g',false,now()) RETURNING conversation_id",
                Long.class);
        try {
            jdbc.update("INSERT INTO conversation_members (conversation_id, user_id, role, joined_at) VALUES (?,?,'MEMBER',now())",
                    convId, alice.getUserId());
            mockMvc.perform(get("/api/v1/users/search").param("q", "tran").param("conversation_id", String.valueOf(convId))
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].user_id").value(bob.getUserId()));
        } finally {
            jdbc.update("DELETE FROM conversation_members WHERE conversation_id = ?", convId);
            jdbc.update("DELETE FROM conversations WHERE conversation_id = ?", convId);
        }
    }
}
