package com.ptiters.marketplace.notification;

import com.ptiters.marketplace.feedback.D1TestSecurityConfig;
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
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(D1TestSecurityConfig.class)
class NotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuthIdentityRepository userAuthIdentityRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ObjectMapper objectMapper;

    private User userA;
    private User userB;
    private User bannedUser;
    private String tokenA;
    private String tokenB;
    private String bannedToken;

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

        userA = ProductTestFixtures.createUser(userRepository, "notif_a@test.com", "User A", UserRole.STUDENT);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, userA, "notif_uid_a");
        tokenA = ProductTestFixtures.createToken("notif_uid_a", userA.getEmail());

        userB = ProductTestFixtures.createUser(userRepository, "notif_b@test.com", "User B", UserRole.STUDENT);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, userB, "notif_uid_b");
        tokenB = ProductTestFixtures.createToken("notif_uid_b", userB.getEmail());

        bannedUser = new User();
        bannedUser.setEmail("notif_banned@test.com");
        bannedUser.setFullName("Banned User");
        bannedUser.setRole(UserRole.STUDENT);
        bannedUser.setStatus(UserStatus.BANNED);
        bannedUser.setPublicId(UUID.randomUUID());
        bannedUser = userRepository.save(bannedUser);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, bannedUser, "notif_uid_banned");
        bannedToken = ProductTestFixtures.createToken("notif_uid_banned", bannedUser.getEmail());
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM notifications", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM user_device_tokens", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class));
    }

    private void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM user_device_tokens");
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM user_auth_identities");
        jdbcTemplate.update("DELETE FROM users");
    }

    private Notification saveNotification(Long userId, NotificationType type, String title, boolean isRead, LocalDateTime createdAt) {
        Notification n = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body("Body for " + title)
                .refType("ORDER")
                .refId(100L)
                .isRead(isRead)
                .createdAt(createdAt)
                .build();
        return notificationRepository.saveAndFlush(n);
    }

    @Test
    @DisplayName("401 khi chưa đăng nhập và 403 khi tài khoản bị BANNED")
    void authAndStatusGuards() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/notifications/read-all"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/notifications/push/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fcm_token\":\"abc\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bannedToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Danh sách thông báo cô lập theo user, sắp xếp created_at DESC, notification_id DESC và keyset cursor JSON đúng chuẩn Python")
    void listNotifications_PaginationAndPythonCursorFormat() throws Exception {
        LocalDateTime base = LocalDateTime.of(2026, 9, 14, 10, 0, 0);
        Notification n1 = saveNotification(userA.getUserId(), NotificationType.SYSTEM, "N1", false, base.minusMinutes(10));
        Notification n2 = saveNotification(userA.getUserId(), NotificationType.SYSTEM, "N2", false, base.minusMinutes(5));
        // Cùng timestamp để kiểm tra tie-break theo notification_id DESC
        Notification n3 = saveNotification(userA.getUserId(), NotificationType.SYSTEM, "N3", false, base);
        Notification n4 = saveNotification(userA.getUserId(), NotificationType.NEW_ORDER, "N4", false, base);
        Notification n5 = saveNotification(userA.getUserId(), NotificationType.SYSTEM, "N5", true, base.plusMinutes(1));
        saveNotification(userB.getUserId(), NotificationType.SYSTEM, "Other B", false, base.plusMinutes(2));

        // Trang 1 (limit=2)
        MvcResult r1 = mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.has_more").value(true))
                .andExpect(jsonPath("$.next_cursor").isNotEmpty())
                .andExpect(jsonPath("$.items[0].notification_id").value(n5.getNotificationId()))
                .andExpect(jsonPath("$.items[1].notification_id").value(n4.getNotificationId()))
                .andReturn();

        JsonNode page1 = objectMapper.readTree(r1.getResponse().getContentAsString());
        String cursor1 = page1.get("next_cursor").asString();

        // Kiểm tra định dạng cursor JSON base64url giống Python
        String decodedJson = new String(Base64.getUrlDecoder().decode(cursor1), StandardCharsets.UTF_8);
        JsonNode cursorObj = objectMapper.readTree(decodedJson);
        assertEquals(n4.getNotificationId(), cursorObj.get("notification_id").asLong());
        assertNotNull(cursorObj.get("created_at").asString());

        // Trang 2 (limit=2)
        MvcResult r2 = mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("limit", "2")
                        .param("cursor", cursor1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.has_more").value(true))
                .andExpect(jsonPath("$.items[0].notification_id").value(n3.getNotificationId()))
                .andExpect(jsonPath("$.items[1].notification_id").value(n2.getNotificationId()))
                .andReturn();

        String cursor2 = objectMapper.readTree(r2.getResponse().getContentAsString()).get("next_cursor").asString();

        // Trang 3 (còn lại 1 item)
        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("limit", "2")
                        .param("cursor", cursor2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.has_more").value(false))
                .andExpect(jsonPath("$.next_cursor").isEmpty())
                .andExpect(jsonPath("$.items[0].notification_id").value(n1.getNotificationId()));

        // Kiểm tra không trùng ID giữa 3 trang
        Set<Long> seen = new HashSet<>();
        for (JsonNode item : page1.get("items")) seen.add(item.get("notification_id").asLong());
        for (JsonNode item : objectMapper.readTree(r2.getResponse().getContentAsString()).get("items")) {
            seen.add(item.get("notification_id").asLong());
        }
        seen.add(n1.getNotificationId());
        assertEquals(5, seen.size());

        // Kiểm tra unread_only=true chỉ trả về 4 thông báo chưa đọc
        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("unread_only", "true")
                        .param("limit", "100")) // kẹp tối đa 50
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.items[*].is_read", everyItem(is(false))));
    }

    @Test
    @DisplayName("Unread count, mark-read idempotent, mark-all-read, xóa BOLA 404")
    void unreadCount_MarkRead_DeleteBola() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Notification a1 = saveNotification(userA.getUserId(), NotificationType.NEW_ORDER, "A1", false, now.minusMinutes(2));
        Notification a2 = saveNotification(userA.getUserId(), NotificationType.SYSTEM, "A2", false, now.minusMinutes(1));
        Notification b1 = saveNotification(userB.getUserId(), NotificationType.SYSTEM, "B1", false, now);

        // Unread count ban đầu của A = 2
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        // Mark-read idempotent (lần 1 & lần 2 đều 204)
        mockMvc.perform(patch("/api/v1/notifications/" + a1.getNotificationId() + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/v1/notifications/" + a1.getNotificationId() + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        // B cố mark-read thông báo của A -> 404 privacy-safe
        mockMvc.perform(patch("/api/v1/notifications/" + a1.getNotificationId() + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Không tìm thấy thông báo."));

        // Unread count của A giảm còn 1
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        // B cố xóa thông báo của A -> 404 và bản ghi vẫn còn
        mockMvc.perform(delete("/api/v1/notifications/" + a2.getNotificationId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        assertTrue(notificationRepository.findById(a2.getNotificationId()).isPresent());

        // A xóa thông báo unread a2 -> 204, unread count giảm về 0
        mockMvc.perform(delete("/api/v1/notifications/" + a2.getNotificationId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNoContent());
        assertFalse(notificationRepository.findById(a2.getNotificationId()).isPresent());
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        // Mark-all-read cho A không ảnh hưởng B
        saveNotification(userA.getUserId(), NotificationType.SYSTEM, "A3", false, now);
        mockMvc.perform(patch("/api/v1/notifications/read-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
        assertFalse(notificationRepository.findById(b1.getNotificationId()).orElseThrow().getIsRead());
    }

    @Test
    @DisplayName("Push subscribe / reassign / unsubscribe hoạt động thật trên user_device_tokens")
    void pushSubscribeAndUnsubscribe() throws Exception {
        // Token rỗng -> 422
        mockMvc.perform(post("/api/v1/notifications/push/subscribe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fcm_token\":\"   \",\"device_info\":\"Chrome\"}"))
                .andExpect(status().isUnprocessableEntity());

        // User A đăng ký token
        mockMvc.perform(post("/api/v1/notifications/push/subscribe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fcm_token\":\"fcm_tok_001\",\"device_info\":\"Chrome Win\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("subscribed"));

        Long owner1 = jdbcTemplate.queryForObject(
                "SELECT user_id FROM user_device_tokens WHERE fcm_token = ?",
                Long.class,
                "fcm_tok_001"
        );
        assertEquals(userA.getUserId(), owner1);

        // User B đăng nhập trên cùng thiết bị (cùng fcm_token) -> reassign sang userB
        mockMvc.perform(post("/api/v1/notifications/push/subscribe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fcm_token\":\"fcm_tok_001\",\"device_info\":\"Edge Win\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("subscribed"));

        Long owner2 = jdbcTemplate.queryForObject(
                "SELECT user_id FROM user_device_tokens WHERE fcm_token = ?",
                Long.class,
                "fcm_tok_001"
        );
        assertEquals(userB.getUserId(), owner2);

        // User A cố hủy token đang thuộc User B -> không xóa của B
        mockMvc.perform(post("/api/v1/notifications/push/unsubscribe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fcm_token\":\"fcm_tok_001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("unsubscribed"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_device_tokens WHERE fcm_token = ?",
                Integer.class,
                "fcm_tok_001"
        ));

        // User B hủy token -> xóa sạch
        mockMvc.perform(post("/api/v1/notifications/push/unsubscribe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fcm_token\":\"fcm_tok_001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("unsubscribed"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_device_tokens WHERE fcm_token = ?",
                Integer.class,
                "fcm_tok_001"
        ));
    }
}
