package com.ptiters.marketplace.communitymoment;

import com.ptiters.marketplace.integration.LocalMediaStorage;
import com.ptiters.marketplace.integration.MediaStorage;
import com.ptiters.marketplace.notification.Notification;
import com.ptiters.marketplace.notification.NotificationRepository;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=3",
        "spring.datasource.hikari.minimum-idle=1"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CommunityMomentControllerIntegrationTest {

    private static final byte[] VALID_JPEG_BYTES = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00, 0x01
    };
    private static final byte[] VALID_PNG_BYTES = new byte[]{
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0x00, 0x00, 0x00, 0x0D
    };
    private static final byte[] VALID_WEBP_BYTES = new byte[]{
            'R', 'I', 'F', 'F', 0x1A, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '
    };
    private static final byte[] SPOOFED_EXE_BYTES = new byte[]{'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00};

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        @Primary
        public JwtDecoder testJwtDecoder() {
            return ProductTestFixtures.createTestJwtDecoder();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommunityMomentRepository momentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuthIdentityRepository userAuthIdentityRepository;

    @Autowired
    private MediaStorage mediaStorage;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    private User student;
    private User bannedStudent;
    private User admin;
    private String studentToken;
    private String bannedToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        verifySafetyBoundary();
        cleanDatabase();

        student = ProductTestFixtures.createUser(userRepository, "cm_student@test.com", "Nguyen Sinh Vien", UserRole.STUDENT);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, student, "cm_student_uid");
        studentToken = ProductTestFixtures.createToken("cm_student_uid", student.getEmail());

        bannedStudent = ProductTestFixtures.createUser(userRepository, "cm_banned@test.com", "Banned User", UserRole.STUDENT);
        bannedStudent.setStatus(UserStatus.BANNED);
        userRepository.saveAndFlush(bannedStudent);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, bannedStudent, "cm_banned_uid");
        bannedToken = ProductTestFixtures.createToken("cm_banned_uid", bannedStudent.getEmail());

        admin = ProductTestFixtures.createUser(userRepository, "cm_admin@test.com", "Admin Reviewer", UserRole.ADMIN);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, admin, "cm_admin_uid");
        adminToken = ProductTestFixtures.createToken("cm_admin_uid", admin.getEmail());
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanDatabase();
    }

    private void verifySafetyBoundary() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT current_database()")) {
            assertTrue(rs.next());
            String dbName = rs.getString(1);
            assertTrue(dbName.matches("marketplace_java_test(_[a-z0-9]+)?"),
                    "SAFETY VIOLATION: tests must only run on marketplace_java_test*: " + dbName);
        }
        assertEquals("false", environment.getProperty("spring.flyway.enabled"));
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
    }

    private void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM community_moments");
        jdbcTemplate.update("DELETE FROM user_auth_identities");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    @DisplayName("POST /api/v1/community-moments - 401 khi chưa đăng nhập, 403 khi tài khoản bị khóa")
    void testSubmitMoment_AuthAndStatusBoundaries() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "moment.jpg", "image/jpeg", VALID_JPEG_BYTES);

        mockMvc.perform(multipart("/api/v1/community-moments")
                        .file(file)
                        .param("note", "Khoảnh khắc chưa đăng nhập"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Missing or invalid Authorization header."));

        mockMvc.perform(multipart("/api/v1/community-moments")
                        .file(file)
                        .param("note", "Khoảnh khắc bị khóa")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bannedToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("User account is banned."));
    }

    @Test
    @DisplayName("POST /api/v1/community-moments - Upload JPEG/PNG/WebP thành công, tạo notification cho Admin và chặn trùng PENDING (409)")
    void testSubmitMoment_SuccessFormatsNotificationAndPendingConflict() throws Exception {
        MockMultipartFile jpeg = new MockMultipartFile("file", "moment.jpg", "image/jpeg", VALID_JPEG_BYTES);
        String noteWithEmoji = "Nhận giáo trình ngay trước giờ lên lớp 😆 ✨";

        mockMvc.perform(multipart("/api/v1/community-moments")
                        .file(jpeg)
                        .param("note", noteWithEmoji)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.moment_id").isNumber())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.created_at").isNotEmpty());

        List<CommunityMoment> savedList = momentRepository.findAll();
        assertEquals(1, savedList.size());
        CommunityMoment first = savedList.get(0);
        assertEquals("image/jpeg", first.getMimeType());
        assertEquals(noteWithEmoji, first.getNote());
        assertEquals(CommunityMomentStatus.PENDING, first.getStatus());

        if (mediaStorage instanceof LocalMediaStorage local) {
            Path stored = local.getBaseDir().resolve(first.getStorageKey());
            assertTrue(Files.exists(stored), "Uploaded file must exist in LocalMediaStorage");
        }

        // Verify admin notification created
        List<Notification> notifications = notificationRepository.findAll();
        assertEquals(1, notifications.size());
        Notification adminNotif = notifications.get(0);
        assertEquals(admin.getUserId(), adminNotif.getUserId());
        assertEquals("ADMIN_COMMUNITY", adminNotif.getRefType());
        assertEquals(first.getMomentId(), adminNotif.getRefId());
        assertEquals("Có ảnh cộng đồng mới chờ duyệt", adminNotif.getTitle());

        // Second submission while PENDING -> 409 with structured detail.code
        MockMultipartFile png = new MockMultipartFile("file", "moment2.png", "image/png", VALID_PNG_BYTES);
        mockMvc.perform(multipart("/api/v1/community-moments")
                        .file(png)
                        .param("note", "Khoảnh khắc thứ 2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail.code").value("COMMUNITY_MOMENT_PENDING"))
                .andExpect(jsonPath("$.detail.message").isNotEmpty());

        // Approve first moment -> user can now submit WebP
        mockMvc.perform(post("/api/v1/admin/community-moments/{id}/approve", first.getMomentId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        MockMultipartFile webp = new MockMultipartFile("file", "moment3.webp", "image/webp", VALID_WEBP_BYTES);
        mockMvc.perform(multipart("/api/v1/community-moments")
                        .file(webp)
                        .param("note", "Ảnh WebP hợp lệ")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/v1/community-moments - Kiểm tra validation ảnh và lời nhắn (422)")
    void testSubmitMoment_ValidationFailures() throws Exception {
        MockMultipartFile validJpg = new MockMultipartFile("file", "ok.jpg", "image/jpeg", VALID_JPEG_BYTES);

        // Missing file
        mockMvc.perform(multipart("/api/v1/community-moments")
                        .param("note", "Lời nhắn hợp lệ")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                .andExpect(status().isUnprocessableEntity());

        // Missing note
        mockMvc.perform(multipart("/api/v1/community-moments")
                        .file(validJpg)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                .andExpect(status().isUnprocessableEntity());

        // Blank note
        mockMvc.perform(multipart("/api/v1/community-moments")
                        .file(validJpg)
                        .param("note", "   ")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                .andExpect(status().isUnprocessableEntity());

        // Multiline note
        mockMvc.perform(multipart("/api/v1/community-moments")
                        .file(validJpg)
                        .param("note", "Dòng 1\nDòng 2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                .andExpect(status().isUnprocessableEntity());

        // > 80 chars
        mockMvc.perform(multipart("/api/v1/community-moments")
                        .file(validJpg)
                        .param("note", "A".repeat(81))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                .andExpect(status().isUnprocessableEntity());

        // Spoofed executable disguised as JPG
        MockMultipartFile spoofed = new MockMultipartFile("file", "evil.jpg", "image/jpeg", SPOOFED_EXE_BYTES);
        mockMvc.perform(multipart("/api/v1/community-moments")
                        .file(spoofed)
                        .param("note", "Lời nhắn hợp lệ")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                .andExpect(status().isUnprocessableEntity());

        // Plain text
        MockMultipartFile text = new MockMultipartFile("file", "fake.png", "image/png", "not-an-image".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/community-moments")
                        .file(text)
                        .param("note", "Lời nhắn hợp lệ")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("DB Partial Unique Index uq_community_moments_user_pending chặn 2 bản ghi PENDING cùng uploader")
    void testPartialUniqueIndex_OnePendingPerUser() {
        CommunityMoment m1 = CommunityMoment.builder()
                .uploaderId(student.getUserId())
                .imageUrl("https://media.local.test/m1.jpg")
                .storageKey("k1")
                .mimeType("image/jpeg")
                .sizeBytes(1000L)
                .note("First")
                .status(CommunityMomentStatus.PENDING)
                .build();
        momentRepository.saveAndFlush(m1);

        CommunityMoment m2 = CommunityMoment.builder()
                .uploaderId(student.getUserId())
                .imageUrl("https://media.local.test/m2.jpg")
                .storageKey("k2")
                .mimeType("image/jpeg")
                .sizeBytes(1000L)
                .note("Second")
                .status(CommunityMomentStatus.PENDING)
                .build();
        assertThrows(DataIntegrityViolationException.class, () -> momentRepository.saveAndFlush(m2));
    }

    @Test
    @DisplayName("GET /api/v1/community-moments/hero - Công khai, tối đa 5 ảnh APPROVED mới nhất theo thứ tự cũ->mới, không lộ PII")
    void testPublicHero_BoundedPrivacyAndOrdering() throws Exception {
        // Empty initially
        mockMvc.perform(get("/api/v1/community-moments/hero"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        // Insert 6 approved + 1 pending + 1 rejected
        for (int i = 1; i <= 6; i++) {
            momentRepository.saveAndFlush(CommunityMoment.builder()
                    .uploaderId(student.getUserId())
                    .imageUrl("https://res.cloudinary.com/ptit/image/upload/v1/hero_" + i + ".jpg")
                    .storageKey("hero_key_" + i)
                    .mimeType("image/jpeg")
                    .sizeBytes(1000L * i)
                    .note(i == 6 ? null : "Khoảnh khắc " + i)
                    .status(CommunityMomentStatus.APPROVED)
                    .reviewedBy(admin.getUserId())
                    .reviewedAt(LocalDateTime.of(2026, 9, 21, 10, i, 0))
                    .build());
        }
        momentRepository.saveAndFlush(CommunityMoment.builder()
                .uploaderId(student.getUserId())
                .imageUrl("https://res.cloudinary.com/ptit/image/upload/v1/hero_pending.jpg")
                .storageKey("hero_pending_key")
                .mimeType("image/jpeg")
                .sizeBytes(999L)
                .note("Pending never public")
                .status(CommunityMomentStatus.PENDING)
                .build());
        momentRepository.saveAndFlush(CommunityMoment.builder()
                .uploaderId(student.getUserId())
                .imageUrl("https://res.cloudinary.com/ptit/image/upload/v1/hero_rejected.jpg")
                .storageKey("hero_rejected_key")
                .mimeType("image/jpeg")
                .sizeBytes(999L)
                .note("Rejected never public")
                .status(CommunityMomentStatus.REJECTED)
                .reviewedBy(admin.getUserId())
                .reviewedAt(LocalDateTime.of(2026, 9, 21, 10, 7, 0))
                .build());

        mockMvc.perform(get("/api/v1/community-moments/hero"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.items[0].note").value("Khoảnh khắc 2"))
                .andExpect(jsonPath("$.items[4].image_url").value(containsString("f_auto,q_auto,w_1600")))
                .andExpect(jsonPath("$.items[4].image_url").value(containsString("hero_6.jpg")))
                .andExpect(jsonPath("$.items[4].note").value(nullValue()))
                .andExpect(jsonPath("$.items[0].uploader_id").doesNotExist())
                .andExpect(jsonPath("$.items[0].storage_key").doesNotExist())
                .andExpect(jsonPath("$.items[0].reviewed_by").doesNotExist());
    }

    @Test
    @DisplayName("Admin queue cursor pagination + authorization 401/403")
    void testAdminQueuePaginationAndAuthorization() throws Exception {
        // Unauthenticated -> 401
        mockMvc.perform(get("/api/v1/admin/community-moments"))
                .andExpect(status().isUnauthorized());

        // Student -> 403
        mockMvc.perform(get("/api/v1/admin/community-moments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Yêu cầu quyền quản trị viên."));

        // Seed 3 APPROVED moments with distinct createdAt
        for (int i = 1; i <= 3; i++) {
            momentRepository.saveAndFlush(CommunityMoment.builder()
                    .uploaderId(student.getUserId())
                    .imageUrl("https://res.cloudinary.com/ptit/image/upload/v1/adm_" + i + ".jpg")
                    .storageKey("adm_key_" + i)
                    .mimeType("image/jpeg")
                    .sizeBytes(1000L * i)
                    .note("Admin item " + i)
                    .status(CommunityMomentStatus.APPROVED)
                    .reviewedBy(admin.getUserId())
                    .reviewedAt(LocalDateTime.of(2026, 9, 21, 11, i, 0))
                    .createdAt(LocalDateTime.of(2026, 9, 21, 10, i, 0))
                    .build());
        }

        // Page 1 (limit=2) -> newest created first (item 3, item 2)
        MvcResult page1Res = mockMvc.perform(get("/api/v1/admin/community-moments")
                        .param("status", "APPROVED")
                        .param("limit", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].note").value("Admin item 3"))
                .andExpect(jsonPath("$.items[1].note").value("Admin item 2"))
                .andExpect(jsonPath("$.has_more").value(true))
                .andExpect(jsonPath("$.next_cursor").isNotEmpty())
                .andReturn();

        String body = page1Res.getResponse().getContentAsString();
        String cursor = com.jayway.jsonpath.JsonPath.read(body, "$.next_cursor");
        Long firstMomentId = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.items[0].moment_id")).longValue();

        // Admin detail 200 & 404
        mockMvc.perform(get("/api/v1/admin/community-moments/{id}", firstMomentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moment_id").value(firstMomentId))
                .andExpect(jsonPath("$.note").value("Admin item 3"))
                .andExpect(jsonPath("$.uploader.user_id").value(student.getUserId()));

        mockMvc.perform(get("/api/v1/admin/community-moments/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        // Page 2 -> item 1, has_more = false
        mockMvc.perform(get("/api/v1/admin/community-moments")
                        .param("status", "APPROVED")
                        .param("limit", "2")
                        .param("cursor", cursor)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].note").value("Admin item 1"))
                .andExpect(jsonPath("$.has_more").value(false));

        // Invalid cursor -> 400
        mockMvc.perform(get("/api/v1/admin/community-moments")
                        .param("cursor", "invalid_cursor")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid pagination cursor."));
    }

    @Test
    @DisplayName("Admin approve (cắt tỉa ảnh thứ 6), reject (hard delete + notification), delete approved (hard delete + asset cleanup)")
    void testAdminLifecycle_ApprovePruneRejectAndDelete() throws Exception {
        // Seed 5 approved moments (one stored in LocalMediaStorage to verify pruning cleanup)
        MediaStorage.UploadedAsset oldestAsset = mediaStorage.uploadCommunityMomentImage(VALID_JPEG_BYTES, "oldest.jpg", student.getUserId());
        CommunityMoment oldest = momentRepository.saveAndFlush(CommunityMoment.builder()
                .uploaderId(student.getUserId())
                .imageUrl(oldestAsset.secureUrl())
                .storageKey(oldestAsset.publicId())
                .mimeType("image/jpeg")
                .sizeBytes(1000L)
                .note("Approved 1")
                .status(CommunityMomentStatus.APPROVED)
                .reviewedBy(admin.getUserId())
                .reviewedAt(LocalDateTime.of(2026, 9, 21, 10, 1, 0))
                .build());

        for (int i = 2; i <= 5; i++) {
            momentRepository.saveAndFlush(CommunityMoment.builder()
                    .uploaderId(student.getUserId())
                    .imageUrl("https://media.local.test/app_" + i + ".jpg")
                    .storageKey("app_key_" + i)
                    .mimeType("image/jpeg")
                    .sizeBytes(1000L)
                    .note("Approved " + i)
                    .status(CommunityMomentStatus.APPROVED)
                    .reviewedBy(admin.getUserId())
                    .reviewedAt(LocalDateTime.of(2026, 9, 21, 10, i, 0))
                    .build());
        }

        // 6th moment submitted via API
        MockMultipartFile sixthFile = new MockMultipartFile("file", "sixth.jpg", "image/jpeg", VALID_JPEG_BYTES);
        MvcResult submitRes = mockMvc.perform(multipart("/api/v1/community-moments")
                        .file(sixthFile)
                        .param("note", "Khoảnh khắc thứ 6")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn();
        Number sixthIdNum = com.jayway.jsonpath.JsonPath.read(submitRes.getResponse().getContentAsString(), "$.moment_id");
        Long sixthId = sixthIdNum.longValue();

        // Approve 6th -> oldest pruned from DB and LocalMediaStorage
        mockMvc.perform(post("/api/v1/admin/community-moments/{id}/approve", sixthId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moment_id").value(sixthId))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewer.user_id").value(admin.getUserId()))
                .andExpect(jsonPath("$.reviewed_at").isNotEmpty());

        assertFalse(momentRepository.existsById(oldest.getMomentId()), "Oldest approved moment must be pruned");
        assertEquals(5, momentRepository.count());
        if (mediaStorage instanceof LocalMediaStorage local) {
            assertFalse(Files.exists(local.getBaseDir().resolve(oldestAsset.publicId())),
                    "Pruned moment file must be removed from LocalMediaStorage");
        }

        // Approving again -> 409
        mockMvc.perform(post("/api/v1/admin/community-moments/{id}/approve", sixthId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isConflict());

        // Delete approved 6th moment
        mockMvc.perform(delete("/api/v1/admin/community-moments/{id}", sixthId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moment_id").value(sixthId))
                .andExpect(jsonPath("$.status").value("DELETED"));
        assertFalse(momentRepository.existsById(sixthId));

        // Submit another moment and reject it
        MockMultipartFile rejectFile = new MockMultipartFile("file", "reject.png", "image/png", VALID_PNG_BYTES);
        MvcResult submitRejRes = mockMvc.perform(multipart("/api/v1/community-moments")
                        .file(rejectFile)
                        .param("note", "Ảnh sẽ bị từ chối")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn();
        Number rejIdNum = com.jayway.jsonpath.JsonPath.read(submitRejRes.getResponse().getContentAsString(), "$.moment_id");
        Long rejId = rejIdNum.longValue();
        String rejStorageKey = momentRepository.findById(rejId).orElseThrow().getStorageKey();

        // Deleting PENDING moment -> 409
        mockMvc.perform(delete("/api/v1/admin/community-moments/{id}", rejId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isConflict());

        // Reject PENDING -> hard delete + COMMUNITY_REJECTED notification + storage cleanup
        mockMvc.perform(post("/api/v1/admin/community-moments/{id}/reject", rejId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moment_id").value(rejId))
                .andExpect(jsonPath("$.status").value("REJECTED"));

        assertFalse(momentRepository.existsById(rejId));
        if (mediaStorage instanceof LocalMediaStorage local) {
            assertFalse(Files.exists(local.getBaseDir().resolve(rejStorageKey)));
        }
        assertTrue(notificationRepository.findAll().stream()
                .anyMatch(n -> n.getUserId().equals(student.getUserId())
                        && "COMMUNITY_REJECTED".equals(n.getRefType())
                        && n.getRefId().equals(rejId)));
        assertTrue(notificationRepository.findAll().stream()
                .anyMatch(n -> n.getUserId().equals(student.getUserId())
                        && "COMMUNITY_MOMENT".equals(n.getRefType())
                        && n.getRefId().equals(sixthId)));
    }
}
