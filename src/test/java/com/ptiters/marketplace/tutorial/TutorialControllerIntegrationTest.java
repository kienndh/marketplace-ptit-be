package com.ptiters.marketplace.tutorial;

import com.jayway.jsonpath.JsonPath;
import com.ptiters.marketplace.integration.LocalMediaStorage;
import com.ptiters.marketplace.integration.MediaStorage;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TutorialControllerIntegrationTest {

    private static final byte[] VALID_MP4_BYTES = new byte[]{
            0x00, 0x00, 0x00, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm',
            0x00, 0x00, 0x02, 0x00, 'i', 's', 'o', 'm', 'm', 'p', '4', '1',
            0x01, 0x02, 0x03, 0x04
    };

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
    private TutorialRepository tutorialRepository;

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

    private User admin;
    private User student;
    private User banned;
    private String adminToken;
    private String studentToken;
    private String bannedToken;

    @BeforeEach
    void setUp() throws Exception {
        verifySafetyBoundary();
        cleanDatabase();

        admin = ProductTestFixtures.createUser(userRepository, "tut_admin@test.com", "Admin Tutorial", UserRole.ADMIN);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, admin, "tut_admin_uid");
        adminToken = ProductTestFixtures.createToken("tut_admin_uid", admin.getEmail());

        student = ProductTestFixtures.createUser(userRepository, "tut_student@test.com", "Student User", UserRole.STUDENT);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, student, "tut_student_uid");
        studentToken = ProductTestFixtures.createToken("tut_student_uid", student.getEmail());

        banned = ProductTestFixtures.createUser(userRepository, "tut_banned@test.com", "Banned User", UserRole.STUDENT);
        banned.setStatus(UserStatus.BANNED);
        userRepository.saveAndFlush(banned);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, banned, "tut_banned_uid");
        bannedToken = ProductTestFixtures.createToken("tut_banned_uid", banned.getEmail());
    }

    @AfterEach
    void tearDown() {
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
        jdbcTemplate.update("DELETE FROM tutorials");
        jdbcTemplate.update("DELETE FROM user_auth_identities");
        jdbcTemplate.update("DELETE FROM users");
    }

    private String stageDirectUploadInLocalStorage(String folder, String leafPublicId, byte[] content) throws Exception {
        String fullPublicId = folder + "/" + leafPublicId;
        if (mediaStorage instanceof LocalMediaStorage local) {
            Path target = local.getBaseDir().resolve(fullPublicId);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        }
        return fullPublicId;
    }

    @Test
    @DisplayName("GET /api/v1/tutorials - Công khai, chỉ trả bản đã xuất bản, sắp xếp tất định và không lộ trường quản trị")
    void testPublicTutorials_ListPublishedAndPrivacy() throws Exception {
        Tutorial draft = tutorialRepository.saveAndFlush(Tutorial.builder()
                .featureKey("HOME")
                .title("Bản nháp")
                .videoUrl("https://res.cloudinary.com/demo/video/upload/v1/draft.mp4")
                .videoStorageKey("student-marketplace/test/tutorials/videos/draft")
                .videoMimeType("video/mp4")
                .videoSizeBytes(1024L)
                .durationMs(10000L)
                .sortOrder(5)
                .isPublished(false)
                .createdBy(admin.getUserId())
                .build());

        Tutorial pub2 = tutorialRepository.saveAndFlush(Tutorial.builder()
                .featureKey("HOME")
                .title("Hướng dẫn 2")
                .videoUrl("https://res.cloudinary.com/demo/video/upload/v1/pub2.mp4")
                .videoStorageKey("student-marketplace/test/tutorials/videos/pub2")
                .videoMimeType("video/mp4")
                .videoSizeBytes(2048L)
                .durationMs(15000L)
                .actionKey("HOME")
                .sortOrder(20)
                .isPublished(true)
                .createdBy(admin.getUserId())
                .build());

        Tutorial pub1 = tutorialRepository.saveAndFlush(Tutorial.builder()
                .featureKey("HOME")
                .title("Hướng dẫn 1")
                .videoUrl("https://res.cloudinary.com/demo/video/upload/v1/pub1.mp4")
                .videoStorageKey("student-marketplace/test/tutorials/videos/pub1")
                .videoMimeType("video/mp4")
                .videoSizeBytes(2048L)
                .durationMs(25000L)
                .actionKey("CREATE_PRODUCT")
                .sortOrder(10)
                .isPublished(true)
                .createdBy(admin.getUserId())
                .build());

        mockMvc.perform(get("/api/v1/tutorials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].tutorial_id").value(pub1.getTutorialId()))
                .andExpect(jsonPath("$[0].title").value("Hướng dẫn 1"))
                .andExpect(jsonPath("$[0].duration_ms").value(25000))
                .andExpect(jsonPath("$[0].thumbnail_url").isNotEmpty())
                .andExpect(jsonPath("$[0].video_url").value(containsString("vc_auto")))
                .andExpect(jsonPath("$[1].tutorial_id").value(pub2.getTutorialId()))
                .andExpect(jsonPath("$[0].video_storage_key").doesNotExist())
                .andExpect(jsonPath("$[0].created_by").doesNotExist())
                .andExpect(jsonPath("$[0].is_published").doesNotExist());

        assertFalse(draft.getIsPublished());
    }

    @Test
    @DisplayName("Admin Tutorials Auth & Role Boundary: 401 không token, 403 với STUDENT/BANNED")
    void testAdminAuthAndRoleBoundary() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tutorials"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/admin/tutorials/upload-signature"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/tutorials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Yêu cầu quyền quản trị viên."));

        mockMvc.perform(post("/api/v1/admin/tutorials/upload-signature")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/tutorials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bannedToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("User account is banned."));
    }

    @Test
    @DisplayName("Direct Upload Flow (upload-signature -> create-direct -> confirm-direct-video -> delete) với LocalMediaStorage")
    void testDirectUploadFlow_WithLocalMediaStorage() throws Exception {
        // 1. Request upload signature
        MvcResult sigRes = mockMvc.perform(post("/api/v1/admin/tutorials/upload-signature")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cloud_name").value("local-test"))
                .andExpect(jsonPath("$.folder").isNotEmpty())
                .andExpect(jsonPath("$.public_id").value(startsWith("tut_")))
                .andExpect(jsonPath("$.upload_session_id").isNotEmpty())
                .andReturn();

        String sigJson = sigRes.getResponse().getContentAsString();
        String folder = JsonPath.read(sigJson, "$.folder");
        String leafPublicId = JsonPath.read(sigJson, "$.public_id");
        String sessionId = JsonPath.read(sigJson, "$.upload_session_id");

        // 2. Simulate browser direct upload to LocalMediaStorage
        String fullPublicId = stageDirectUploadInLocalStorage(folder, leafPublicId, VALID_MP4_BYTES);

        // 3. Reject mismatched session/public_id -> 400
        String badSessionBody = """
                {
                    "feature_key": "HOME",
                    "title": "Bad Session",
                    "public_id": "%s",
                    "upload_session_id": "wrong_session",
                    "duration_ms": 12000
                }
                """.formatted(fullPublicId);
        mockMvc.perform(post("/api/v1/admin/tutorials/create-direct")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badSessionBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("không khớp với phiên tải lên")));

        // 4. Reject duration_ms <= 0 -> 400
        String badDurationBody = """
                {
                    "feature_key": "HOME",
                    "title": "Bad Duration",
                    "public_id": "%s",
                    "upload_session_id": "%s",
                    "duration_ms": 0
                }
                """.formatted(fullPublicId, sessionId);
        mockMvc.perform(post("/api/v1/admin/tutorials/create-direct")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badDurationBody))
                .andExpect(status().isBadRequest());

        // Re-stage file because invalid verification cleans up asset
        fullPublicId = stageDirectUploadInLocalStorage(folder, leafPublicId, VALID_MP4_BYTES);

        // 5. Valid create-direct -> 201 Created
        String createBody = """
                {
                    "feature_key": "MY_LISTINGS",
                    "title": "Quản lý tin đăng",
                    "description": "Hướng dẫn từng bước",
                    "action_key": "MY_LISTINGS",
                    "is_published": true,
                    "public_id": "%s",
                    "upload_session_id": "%s",
                    "duration_ms": 18500
                }
                """.formatted(fullPublicId, sessionId);
        MvcResult createRes = mockMvc.perform(post("/api/v1/admin/tutorials/create-direct")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tutorial_id").isNumber())
                .andExpect(jsonPath("$.feature_key").value("MY_LISTINGS"))
                .andExpect(jsonPath("$.video_storage_key").value(fullPublicId))
                .andExpect(jsonPath("$.video_mime_type").value("video/mp4"))
                .andExpect(jsonPath("$.video_size_bytes").value(VALID_MP4_BYTES.length))
                .andExpect(jsonPath("$.duration_ms").value(18500))
                .andExpect(jsonPath("$.sort_order").value(10))
                .andExpect(jsonPath("$.is_published").value(true))
                .andExpect(jsonPath("$.thumbnail_url").value(containsString(fullPublicId)))
                .andReturn();

        Long tutId = ((Number) JsonPath.read(createRes.getResponse().getContentAsString(), "$.tutorial_id")).longValue();

        // 6. Duplicate storage key on create-direct -> 400
        mockMvc.perform(post("/api/v1/admin/tutorials/create-direct")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("đã thuộc về một video hướng dẫn khác")));

        // 7. Idempotent confirm-direct-video replay with same public_id -> 200 OK
        String idempotentConfirmBody = """
                {
                    "public_id": "%s",
                    "upload_session_id": "%s"
                }
                """.formatted(fullPublicId, sessionId);
        mockMvc.perform(post("/api/v1/admin/tutorials/{id}/confirm-direct-video", tutId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(idempotentConfirmBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.video_storage_key").value(fullPublicId));

        // 8. Replace video via confirm-direct-video -> cleans up old file from LocalMediaStorage
        MvcResult sigRes2 = mockMvc.perform(post("/api/v1/admin/tutorials/upload-signature")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        String folder2 = JsonPath.read(sigRes2.getResponse().getContentAsString(), "$.folder");
        String leaf2 = JsonPath.read(sigRes2.getResponse().getContentAsString(), "$.public_id");
        String session2 = JsonPath.read(sigRes2.getResponse().getContentAsString(), "$.upload_session_id");
        byte[] newVideoBytes = new byte[VALID_MP4_BYTES.length + 10];
        System.arraycopy(VALID_MP4_BYTES, 0, newVideoBytes, 0, VALID_MP4_BYTES.length);
        String newFullPublicId = stageDirectUploadInLocalStorage(folder2, leaf2, newVideoBytes);

        String confirmBody = """
                {
                    "public_id": "%s",
                    "upload_session_id": "%s",
                    "duration_ms": 22000
                }
                """.formatted(newFullPublicId, session2);
        mockMvc.perform(post("/api/v1/admin/tutorials/{id}/confirm-direct-video", tutId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.video_storage_key").value(newFullPublicId))
                .andExpect(jsonPath("$.video_size_bytes").value(newVideoBytes.length))
                .andExpect(jsonPath("$.duration_ms").value(22000));

        if (mediaStorage instanceof LocalMediaStorage local) {
            assertFalse(Files.exists(local.getBaseDir().resolve(fullPublicId)), "Old video file must be destroyed");
            assertTrue(Files.exists(local.getBaseDir().resolve(newFullPublicId)), "New video file must remain");
        }

        // 9. Delete tutorial -> hard delete + cleans up new video
        mockMvc.perform(delete("/api/v1/admin/tutorials/{id}", tutId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã xóa Tutorial thành công"));

        assertFalse(tutorialRepository.existsById(tutId));
        if (mediaStorage instanceof LocalMediaStorage local) {
            assertFalse(Files.exists(local.getBaseDir().resolve(newFullPublicId)));
        }
    }

    @Test
    @DisplayName("Multipart create, replace video, metadata update, feature move & reorder")
    void testMultipartUpdateMoveAndReorder() throws Exception {
        // Invalid feature_key -> 400
        MockMultipartFile mp4 = new MockMultipartFile("video", "tut1.mp4", "video/mp4", VALID_MP4_BYTES);
        mockMvc.perform(multipart("/api/v1/admin/tutorials")
                        .file(mp4)
                        .param("feature_key", "LEGACY_INVALID")
                        .param("title", "Bad Key")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("không hợp lệ")));

        // Create two tutorials in FAVORITES
        MvcResult r1 = mockMvc.perform(multipart("/api/v1/admin/tutorials")
                        .file(mp4)
                        .param("feature_key", "FAVORITES")
                        .param("title", "Tin đã lưu 1")
                        .param("description", "Mô tả ban đầu")
                        .param("action_key", "FAVORITES")
                        .param("is_published", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sort_order").value(10))
                .andReturn();
        Long id1 = ((Number) JsonPath.read(r1.getResponse().getContentAsString(), "$.tutorial_id")).longValue();

        MvcResult r2 = mockMvc.perform(multipart("/api/v1/admin/tutorials")
                        .file(mp4)
                        .param("feature_key", "FAVORITES")
                        .param("title", "Tin đã lưu 2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sort_order").value(20))
                .andReturn();
        Long id2 = ((Number) JsonPath.read(r2.getResponse().getContentAsString(), "$.tutorial_id")).longValue();

        // Partial PATCH (publish toggle) preserves description & action_key
        mockMvc.perform(patch("/api/v1/admin/tutorials/{id}", id1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"is_published\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_published").value(false))
                .andExpect(jsonPath("$.description").value("Mô tả ban đầu"))
                .andExpect(jsonPath("$.action_key").value("FAVORITES"));

        // Explicit null clears description & action_key
        mockMvc.perform(patch("/api/v1/admin/tutorials/{id}", id1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": null, \"action_key\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.action_key").value(nullValue()));

        // Admin list & detail 200 / 404
        mockMvc.perform(get("/api/v1/admin/tutorials")
                        .param("feature_key", "FAVORITES")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].tutorial_id").value(id1));

        mockMvc.perform(get("/api/v1/admin/tutorials/{id}", id1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tutorial_id").value(id1))
                .andExpect(jsonPath("$.feature_key").value("FAVORITES"));

        mockMvc.perform(get("/api/v1/admin/tutorials/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        // Reorder FAVORITES: [id2, id1] -> sort_order 10, 20
        mockMvc.perform(patch("/api/v1/admin/tutorials/reorder")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feature_key\": \"FAVORITES\", \"tutorial_ids\": [%d, %d]}".formatted(id2, id1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tutorial_id").value(id2))
                .andExpect(jsonPath("$[0].sort_order").value(10))
                .andExpect(jsonPath("$[1].tutorial_id").value(id1))
                .andExpect(jsonPath("$[1].sort_order").value(20));

        // Duplicate IDs in reorder -> 422
        mockMvc.perform(patch("/api/v1/admin/tutorials/reorder")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feature_key\": \"FAVORITES\", \"tutorial_ids\": [%d, %d]}".formatted(id1, id1)))
                .andExpect(status().isUnprocessableEntity());

        // Replace video multipart -> cleans up old file
        String oldKey1 = tutorialRepository.findById(id1).orElseThrow().getVideoStorageKey();
        MockMultipartFile newMp4 = new MockMultipartFile("video", "replaced.mp4", "video/mp4", VALID_MP4_BYTES);
        MvcResult repRes = mockMvc.perform(multipart("/api/v1/admin/tutorials/{id}/video", id1)
                        .file(newMp4)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        String newKey1 = JsonPath.read(repRes.getResponse().getContentAsString(), "$.video_storage_key");
        assertNotEquals(oldKey1, newKey1);

        // Move id1 to HOME -> sort_order becomes 10
        mockMvc.perform(patch("/api/v1/admin/tutorials/{id}", id1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feature_key\": \"HOME\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feature_key").value("HOME"))
                .andExpect(jsonPath("$.sort_order").value(10));

        // Clean local files via API delete
        mockMvc.perform(delete("/api/v1/admin/tutorials/{id}", id1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/admin/tutorials/{id}", id2)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/admin/tutorials/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DB CHECK constraints tutorials (video_size_bytes > 0, duration_ms > 0, sort_order >= 0) thực thi trên PostgreSQL thật")
    void testDatabaseCheckConstraints_Enforced() {
        assertThrows(DataIntegrityViolationException.class, () -> tutorialRepository.saveAndFlush(
                Tutorial.builder()
                        .featureKey("HOME")
                        .title("Zero size")
                        .videoUrl("https://media.local.test/a.mp4")
                        .videoStorageKey("k1")
                        .videoMimeType("video/mp4")
                        .videoSizeBytes(0L)
                        .sortOrder(10)
                        .isPublished(false)
                        .createdBy(admin.getUserId())
                        .build()
        ));

        assertThrows(DataIntegrityViolationException.class, () -> tutorialRepository.saveAndFlush(
                Tutorial.builder()
                        .featureKey("HOME")
                        .title("Zero duration")
                        .videoUrl("https://media.local.test/b.mp4")
                        .videoStorageKey("k2")
                        .videoMimeType("video/mp4")
                        .videoSizeBytes(100L)
                        .durationMs(0L)
                        .sortOrder(10)
                        .isPublished(false)
                        .createdBy(admin.getUserId())
                        .build()
        ));

        assertThrows(DataIntegrityViolationException.class, () -> tutorialRepository.saveAndFlush(
                Tutorial.builder()
                        .featureKey("HOME")
                        .title("Negative order")
                        .videoUrl("https://media.local.test/c.mp4")
                        .videoStorageKey("k3")
                        .videoMimeType("video/mp4")
                        .videoSizeBytes(100L)
                        .sortOrder(-1)
                        .isPublished(false)
                        .createdBy(admin.getUserId())
                        .build()
        ));
    }
}
