package com.ptiters.marketplace.feedback;

import com.ptiters.marketplace.product.ProductTestFixtures;
import com.ptiters.marketplace.user.*;
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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(D1TestSecurityConfig.class)
class SupportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuthIdentityRepository userAuthIdentityRepository;

    @Autowired
    private SupportContributionRepository supportContributionRepository;

    @Autowired
    private SupportClaimRepository supportClaimRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ObjectMapper objectMapper;

    private User student1;
    private User student2;
    private User banned;
    private User admin;
    private String token1;
    private String token2;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT current_database()")) {
            assertTrue(rs.next());
            assertTrue(rs.getString(1).matches("marketplace_java_test(_[a-z0-9]+)?"),
                    "Chỉ được chạy test trên marketplace_java_test*");
        }
        cleanDatabase();

        student1 = ProductTestFixtures.createUser(userRepository, "sup_s1@test.com", "Nguyễn Văn Một", UserRole.STUDENT);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, student1, "sup_uid_1");
        token1 = ProductTestFixtures.createToken("sup_uid_1", student1.getEmail());

        student2 = ProductTestFixtures.createUser(userRepository, "sup_s2@test.com", "Trần Thị Hai", UserRole.STUDENT);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, student2, "sup_uid_2");
        token2 = ProductTestFixtures.createToken("sup_uid_2", student2.getEmail());

        banned = new User();
        banned.setEmail("sup_banned@test.com");
        banned.setFullName("Banned Supporter");
        banned.setRole(UserRole.STUDENT);
        banned.setStatus(UserStatus.BANNED);
        banned.setPublicId(UUID.randomUUID());
        banned = userRepository.save(banned);

        admin = ProductTestFixtures.createUser(userRepository, "sup_admin@test.com", "Admin Đối Soát", UserRole.ADMIN);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, admin, "sup_uid_admin");
        adminToken = ProductTestFixtures.createToken("sup_uid_admin", admin.getEmail());
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM support_claims", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM support_contributions", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM notifications", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class));
    }

    private void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM support_claims");
        jdbcTemplate.update("DELETE FROM support_contributions");
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM user_auth_identities");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    @DisplayName("GET /support công khai và bảo vệ 401/403 cho các route admin support")
    void publicSupportInfoAndAdminGuards() throws Exception {
        mockMvc.perform(get("/api/v1/feedback/support"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fixed_qr_url").value("/support/nguyen-khac-thinh-vietqr.png"))
                .andExpect(jsonPath("$.support_message").value("Quan trọng là tấm lòng 💛"));

        mockMvc.perform(get("/api/v1/feedback/support/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        mockMvc.perform(post("/api/v1/feedback/support/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimed_amount_vnd\":50000,\"claimed_transferred_at\":\"2026-09-25T10:30:00Z\",\"sender_account_name\":\"A\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/support-claims"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/support-claims")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/support-contributions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin support contributions, tìm kiếm supporter, ẩn/hiện và bảng tri ân công khai")
    void adminContributionsAndPublicLeaderboard() throws Exception {
        OffsetDateTime t0 = OffsetDateTime.of(2026, 9, 20, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime t1 = t0.plusDays(1);

        // Search supporter theo tên và ID, không chứa user BANNED
        mockMvc.perform(get("/api/v1/admin/support-contributions/search-supporters")
                        .param("query", String.valueOf(student1.getUserId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].user_id").value(student1.getUserId()))
                .andExpect(jsonPath("$[0].profile_path").value("/users/" + student1.getUserId()))
                .andExpect(jsonPath("$[0].email").doesNotExist());

        // Số tiền <= 0 -> 422
        mockMvc.perform(post("/api/v1/admin/support-contributions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"supporter_id": %d, "amount_vnd": 0, "received_at": "%s"}
                                """, student1.getUserId(), t0)))
                .andExpect(status().isUnprocessableEntity());

        // Tạo 1 khoản public 100k + 1 khoản private 500k cho student1
        mockMvc.perform(post("/api/v1/admin/support-contributions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"supporter_id": %d, "amount_vnd": 100000, "received_at": "%s", "is_public": true, "transaction_ref": "FT01"}
                                """, student1.getUserId(), t0)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.is_public").value(true))
                .andExpect(jsonPath("$.recorded_by.user_id").value(admin.getUserId()));

        MvcResult privRes = mockMvc.perform(post("/api/v1/admin/support-contributions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"supporter_id": %d, "amount_vnd": 500000, "received_at": "%s", "is_public": false}
                                """, student1.getUserId(), t1)))
                .andExpect(status().isCreated())
                .andReturn();
        long privId = objectMapper.readTree(privRes.getResponse().getContentAsString()).get("contribution_id").asLong();

        // Tạo 1 khoản public 200k cho student2 và 1 khoản public 999k cho banned user
        mockMvc.perform(post("/api/v1/admin/support-contributions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"supporter_id": %d, "amount_vnd": 200000, "received_at": "%s", "is_public": true}
                                """, student2.getUserId(), t1)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/admin/support-contributions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"supporter_id": %d, "amount_vnd": 999000, "received_at": "%s", "is_public": true}
                                """, banned.getUserId(), t1)))
                .andExpect(status().isCreated());

        // Bảng tri ân công khai: student2 (200k) #1, student1 (100k - không cộng 500k private) #2, banned không xuất hiện
        MvcResult lbRes = mockMvc.perform(get("/api/v1/feedback/support/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].rank").value(1))
                .andExpect(jsonPath("$.items[0].supporter.public_id").value(student2.getPublicId().toString()))
                .andExpect(jsonPath("$.items[0].total_amount_vnd").value(200000))
                .andExpect(jsonPath("$.items[1].rank").value(2))
                .andExpect(jsonPath("$.items[1].supporter.public_id").value(student1.getPublicId().toString()))
                .andExpect(jsonPath("$.items[1].total_amount_vnd").value(100000))
                .andReturn();

        JsonNode firstSupporter = objectMapper.readTree(lbRes.getResponse().getContentAsString())
                .get("items").get(0).get("supporter");
        assertNull(firstSupporter.get("user_id"));
        assertEquals("/profile/" + student2.getPublicId(), firstSupporter.get("profile_path").asString());

        // Bật public cho khoản 500k của student1 -> trả 200 + DTO đầy đủ -> student1 lên hạng #1 (600k)
        mockMvc.perform(patch("/api/v1/admin/support-contributions/" + privId + "/visibility")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"is_public\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_public").value(true));

        mockMvc.perform(get("/api/v1/feedback/support/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].supporter.public_id").value(student1.getPublicId().toString()))
                .andExpect(jsonPath("$.items[0].total_amount_vnd").value(600000));

        // Xóa khoản privId -> 204
        mockMvc.perform(delete("/api/v1/admin/support-contributions/" + privId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        assertTrue(supportContributionRepository.findById(privId).isEmpty());
    }

    @Test
    @DisplayName("Luồng support claims: tạo claim -> verify (tạo contribution + notification + 409 khi lặp) -> reject (không tạo contribution + notification + 409)")
    void supportClaimsVerifyAndRejectFlow() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Validation: số tiền <= 0 hoặc sender_account_name rỗng -> 422
        mockMvc.perform(post("/api/v1/feedback/support/claims")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"claimed_amount_vnd": 0, "claimed_transferred_at": "%s", "sender_account_name": "NGUYEN VAN MOT"}
                                """, now)))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/v1/feedback/support/claims")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"claimed_amount_vnd": 50000, "claimed_transferred_at": "%s", "sender_account_name": "   "}
                                """, now)))
                .andExpect(status().isUnprocessableEntity());

        // Student1 gửi claim 1 (wants_public = true)
        MvcResult c1Res = mockMvc.perform(post("/api/v1/feedback/support/claims")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"claimed_amount_vnd": 50000, "claimed_transferred_at": "%s", "sender_account_name": "  NGUYEN VAN MOT  ", "wants_public": true}
                                """, now)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.sender_account_name").value("NGUYEN VAN MOT"))
                .andExpect(jsonPath("$.wants_public").value(true))
                .andReturn();
        long claim1Id = objectMapper.readTree(c1Res.getResponse().getContentAsString()).get("claim_id").asLong();

        // Student2 gửi claim 2 (wants_public mặc định false)
        MvcResult c2Res = mockMvc.perform(post("/api/v1/feedback/support/claims")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"claimed_amount_vnd": 75000, "claimed_transferred_at": "%s", "sender_account_name": "TRAN THI HAI"}
                                """, now)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.wants_public").value(false))
                .andReturn();
        long claim2Id = objectMapper.readTree(c2Res.getResponse().getContentAsString()).get("claim_id").asLong();

        // Admin nhận 2 thông báo ADMIN_SUPPORT_CLAIM
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notifications WHERE user_id = ? AND ref_type = 'ADMIN_SUPPORT_CLAIM'",
                Integer.class,
                admin.getUserId()
        ));

        // Student1 chỉ thấy claim của chính mình
        mockMvc.perform(get("/api/v1/feedback/support/claims/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].claim_id").value(claim1Id));

        // Admin lọc PENDING
        mockMvc.perform(get("/api/v1/admin/support-claims")
                        .param("status", "PENDING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)));

        // Admin verify claim1 -> 200 OK, tạo contribution (is_public=true) + thông báo cho student1
        MvcResult verifyRes = mockMvc.perform(post("/api/v1/admin/support-claims/" + claim1Id + "/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"amount_vnd": 60000, "received_at": "%s", "transaction_ref": "FT_VERIFY_1"}
                                """, now)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.contribution_id").isNumber())
                .andExpect(jsonPath("$.reviewed_by.user_id").value(admin.getUserId()))
                .andReturn();

        long contribId = objectMapper.readTree(verifyRes.getResponse().getContentAsString())
                .get("contribution_id").asLong();
        SupportContribution createdContrib = supportContributionRepository.findById(contribId).orElseThrow();
        assertEquals(60000L, createdContrib.getAmountVnd());
        assertTrue(createdContrib.getIsPublic());

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notifications WHERE user_id = ? AND ref_type = 'SUPPORT_CLAIM' AND ref_id = ?",
                Integer.class,
                student1.getUserId(),
                claim1Id
        ));

        // Verify hoặc reject lại claim1 -> 409 Conflict
        mockMvc.perform(post("/api/v1/admin/support-claims/" + claim1Id + "/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"amount_vnd": 60000, "received_at": "%s"}
                                """, now)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/admin/support-claims/" + claim1Id + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejection_reason\":\"Trùng\"}"))
                .andExpect(status().isConflict());

        // Admin reject claim2 -> 200 OK, KHÔNG tạo contribution, gửi thông báo cho student2
        mockMvc.perform(post("/api/v1/admin/support-claims/" + claim2Id + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejection_reason\":\"Không tìm thấy giao dịch trên sao kê.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.contribution_id").isEmpty())
                .andExpect(jsonPath("$.rejection_reason").value("Không tìm thấy giao dịch trên sao kê."));

        assertEquals(1, supportContributionRepository.count());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notifications WHERE user_id = ? AND ref_type = 'SUPPORT_CLAIM' AND ref_id = ?",
                Integer.class,
                student2.getUserId(),
                claim2Id
        ));

        // Reject lại claim2 -> 409 Conflict
        mockMvc.perform(post("/api/v1/admin/support-claims/" + claim2Id + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }
}
