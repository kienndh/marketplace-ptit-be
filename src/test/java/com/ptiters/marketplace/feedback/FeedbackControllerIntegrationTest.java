package com.ptiters.marketplace.feedback;

import com.ptiters.marketplace.integration.LocalMediaStorage;
import com.ptiters.marketplace.integration.MediaStorage;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(D1TestSecurityConfig.class)
class FeedbackControllerIntegrationTest {

    private static final byte[] VALID_JPEG = concat(
            new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00},
            new byte[64]
    );
    private static final byte[] VALID_PNG = concat(
            new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0x00, 0x00, 0x00, 0x0D},
            new byte[64]
    );
    private static final byte[] VALID_MP4 = concat(
            new byte[]{0x00, 0x00, 0x00, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'},
            new byte[64]
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuthIdentityRepository userAuthIdentityRepository;

    @Autowired
    private SiteFeedbackRepository siteFeedbackRepository;

    @Autowired
    private SiteFeedbackAttachmentRepository siteFeedbackAttachmentRepository;

    @Autowired
    private MediaStorage mediaStorage;

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
    private String bannedToken;
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

        student1 = ProductTestFixtures.createUser(userRepository, "fb_s1@test.com", "Sinh Viên 1", UserRole.STUDENT);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, student1, "fb_uid_1");
        token1 = ProductTestFixtures.createToken("fb_uid_1", student1.getEmail());

        student2 = ProductTestFixtures.createUser(userRepository, "fb_s2@test.com", "Sinh Viên 2", UserRole.STUDENT);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, student2, "fb_uid_2");
        token2 = ProductTestFixtures.createToken("fb_uid_2", student2.getEmail());

        banned = new User();
        banned.setEmail("fb_banned@test.com");
        banned.setFullName("Banned User");
        banned.setRole(UserRole.STUDENT);
        banned.setStatus(UserStatus.BANNED);
        banned.setPublicId(UUID.randomUUID());
        banned = userRepository.save(banned);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, banned, "fb_uid_banned");
        bannedToken = ProductTestFixtures.createToken("fb_uid_banned", banned.getEmail());

        admin = ProductTestFixtures.createUser(userRepository, "fb_admin@test.com", "Admin Feedback", UserRole.ADMIN);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, admin, "fb_uid_admin");
        adminToken = ProductTestFixtures.createToken("fb_uid_admin", admin.getEmail());
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM site_feedback_attachments", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM site_feedback", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM notifications", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class));
    }

    private void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM site_feedback_attachments");
        jdbcTemplate.update("DELETE FROM site_feedback");
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM user_auth_identities");
        jdbcTemplate.update("DELETE FROM users");
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    @Test
    @DisplayName("Phân quyền 401/403 cho feedback và admin feedback")
    void authAndAdminGuards() throws Exception {
        mockMvc.perform(multipart("/api/v1/feedback").param("content", "Nội dung kiểm thử."))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/feedback/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/feedback"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(multipart("/api/v1/feedback")
                        .param("content", "Người dùng bị khóa gửi.")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bannedToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/feedback")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Gửi feedback kèm ảnh/video qua LocalMediaStorage, thông báo Admin, xem công khai ẩn danh, stream 200/206 và hard delete")
    void submitFeedbackWithAttachments_AdminStream_DeleteCleanup() throws Exception {
        MockMultipartFile img1 = new MockMultipartFile("images", "shot1.jpg", "image/jpeg", VALID_JPEG);
        MockMultipartFile img2 = new MockMultipartFile("images", "shot2.png", "image/png", VALID_PNG);
        MockMultipartFile vid = new MockMultipartFile("video", "clip.mp4", "video/mp4", VALID_MP4);

        MvcResult createRes = mockMvc.perform(multipart("/api/v1/feedback")
                        .file(img1)
                        .file(img2)
                        .file(vid)
                        .param("content", "Không thể bấm đặt giá khi bàn phím đang mở 💛")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student1Token()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.feedback_id").isNumber())
                .andExpect(jsonPath("$.attachments", hasSize(3)))
                .andExpect(jsonPath("$.attachments[0].kind").value("IMAGE"))
                .andExpect(jsonPath("$.attachments[1].kind").value("IMAGE"))
                .andExpect(jsonPath("$.attachments[2].kind").value("VIDEO"))
                .andReturn();

        long fbId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("feedback_id").asLong();

        // Admin nhận notification SYSTEM ref_type=ADMIN_FEEDBACK
        Integer adminNotifCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'SYSTEM' AND ref_type = 'ADMIN_FEEDBACK' AND ref_id = ?",
                Integer.class,
                admin.getUserId(),
                fbId
        );
        assertEquals(1, adminNotifCount);

        // Kiểm tra file thật tồn tại trong LocalMediaStorage
        LocalMediaStorage local = assertInstanceOf(LocalMediaStorage.class, mediaStorage);
        var savedAtts = siteFeedbackAttachmentRepository.findAll();
        assertEquals(3, savedAtts.size());
        for (SiteFeedbackAttachment att : savedAtts) {
            assertTrue(Files.exists(local.getBaseDir().resolve(att.getStorageKey())));
        }

        // Bảng tin công khai (unauthenticated) chỉ chứa feedback_id, content, created_at
        MvcResult pubRes = mockMvc.perform(get("/api/v1/feedback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andReturn();
        JsonNode pubItem = objectMapper.readTree(pubRes.getResponse().getContentAsString()).get("items").get(0);
        assertEquals(3, pubItem.size());
        assertNull(pubItem.get("user_id"));
        assertNull(pubItem.get("attachments"));

        // Danh sách của tôi có attachments
        mockMvc.perform(get("/api/v1/feedback/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].attachments", hasSize(3)));

        // Admin list + detail + stream evidence (full 200 & Range 206)
        mockMvc.perform(get("/api/v1/admin/feedback")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sender.user_id").value(student1.getUserId()))
                .andExpect(jsonPath("$.items[0].image_count").value(2))
                .andExpect(jsonPath("$.items[0].video_count").value(1));

        MvcResult detailRes = mockMvc.perform(get("/api/v1/admin/feedback/" + fbId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments", hasSize(3)))
                .andReturn();

        long attId = objectMapper.readTree(detailRes.getResponse().getContentAsString())
                .get("attachments").get(0).get("attachment_id").asLong();

        mockMvc.perform(get("/api/v1/admin/feedback/" + fbId + "/evidence/" + attId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(content().bytes(VALID_JPEG));

        mockMvc.perform(get("/api/v1/admin/feedback/" + fbId + "/evidence/" + attId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .header(HttpHeaders.RANGE, "bytes=0-9"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 0-9/" + VALID_JPEG.length));

        // Người khác xóa -> 404 privacy-safe
        mockMvc.perform(delete("/api/v1/feedback/" + fbId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token2))
                .andExpect(status().isNotFound());

        // Chủ sở hữu xóa -> 204, DB và file LocalMediaStorage đều bị xóa
        mockMvc.perform(delete("/api/v1/feedback/" + fbId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                .andExpect(status().isNoContent());

        assertTrue(siteFeedbackRepository.findById(fbId).isEmpty());
        for (SiteFeedbackAttachment att : savedAtts) {
            Path p = local.getBaseDir().resolve(att.getStorageKey());
            assertFalse(Files.exists(p), "File media phải được dọn dẹp sau khi xóa feedback");
        }
    }

    @Test
    @DisplayName("Validation nội dung, giới hạn số file, magic bytes và phân trang cursor")
    void validationAndCursorPagination() throws Exception {
        // Nội dung quá ngắn -> 422
        mockMvc.perform(multipart("/api/v1/feedback")
                        .param("content", "abc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                .andExpect(status().isUnprocessableEntity());

        // Quá 3 ảnh -> 422
        mockMvc.perform(multipart("/api/v1/feedback")
                        .file(new MockMultipartFile("images", "1.jpg", "image/jpeg", VALID_JPEG))
                        .file(new MockMultipartFile("images", "2.jpg", "image/jpeg", VALID_JPEG))
                        .file(new MockMultipartFile("images", "3.jpg", "image/jpeg", VALID_JPEG))
                        .file(new MockMultipartFile("images", "4.jpg", "image/jpeg", VALID_JPEG))
                        .param("content", "Thử gửi vượt quá 3 ảnh.")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                .andExpect(status().isUnprocessableEntity());

        // File thực thi giả mạo JPEG -> 422
        mockMvc.perform(multipart("/api/v1/feedback")
                        .file(new MockMultipartFile("images", "evil.jpg", "image/jpeg",
                                new byte[]{'M', 'Z', 0x00, 0x01, 0x02, 0x03}))
                        .param("content", "Thử tải lên tệp thực thi.")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                .andExpect(status().isUnprocessableEntity());

        // Tạo 3 feedback cho student1 để kiểm tra cursor pagination & multiple feedbacks per user
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(multipart("/api/v1/feedback")
                            .param("content", "Phản hồi kiểm thử số " + i)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                    .andExpect(status().isCreated());
        }

        // Bảng tin công khai trang 1 (limit=2) -> trang 2 (limit=2)
        MvcResult p1 = mockMvc.perform(get("/api/v1/feedback").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.has_more").value(true))
                .andReturn();
        String cursor = objectMapper.readTree(p1.getResponse().getContentAsString()).get("next_cursor").asString();

        mockMvc.perform(get("/api/v1/feedback").param("limit", "2").param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.has_more").value(false));
    }

    private String student1Token() {
        return token1;
    }
}
