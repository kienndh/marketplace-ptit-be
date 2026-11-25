package com.ptiters.marketplace.report;

import com.ptiters.marketplace.category.Category;
import com.ptiters.marketplace.integration.LocalMediaStorage;
import com.ptiters.marketplace.integration.MediaStorage;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=3")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TrackCTestConfig.class)
class ReportIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TrackCFixtures fx;
    @Autowired JdbcTemplate jdbc;
    @Autowired MediaStorage mediaStorage;

    User admin;
    User reporter;
    User target;
    User other;

    @BeforeEach
    void setUp() {
        fx.cleanAll();
        TrackCTestConfig.reset();
        admin = fx.user("admin", UserRole.ADMIN);
        reporter = fx.user("reporter", UserRole.STUDENT);
        target = fx.user("target", UserRole.STUDENT);
        other = fx.user("other", UserRole.STUDENT);
    }

    @AfterEach
    void tearDown() {
        fx.cleanAll();
        assertEquals(0, fx.count("reports"));
        assertEquals(0, fx.count("report_attachments"));
        assertEquals(0, fx.count("notifications"));
        assertEquals(0, fx.count("users"));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, User u) {
        return b.header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token(u)).contentType(MediaType.APPLICATION_JSON);
    }

    private static byte[] jpeg() {
        byte[] b = new byte[64];
        b[0] = (byte) 0xFF; b[1] = (byte) 0xD8; b[2] = (byte) 0xFF; b[3] = (byte) 0xE0;
        return b;
    }

    private static byte[] mp4() {
        byte[] b = new byte[64];
        System.arraycopy(new byte[]{0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'}, 0, b, 0, 12);
        return b;
    }

    private long reportViaApi(User by, String body) throws Exception {
        String res = mockMvc.perform(json(post("/api/v1/reports"), by).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(res, "$.report_id")).longValue();
    }

    private String userReportBody() {
        return "{\"target_type\":\"USER\",\"target_public_id\":\"" + target.getPublicId() + "\",\"reason\":\"SCAM\",\"description\":\"  lừa đảo  \"}";
    }

    @Test
    @DisplayName("Tạo báo cáo USER: 201, không lộ reporter_id, admin ACTIVE nhận notification ADMIN_REPORT")
    void createUserReport() throws Exception {
        mockMvc.perform(json(post("/api/v1/reports"), reporter).content(userReportBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.target_type").value("USER"))
                .andExpect(jsonPath("$.target_id").value(target.getUserId()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.description").value("lừa đảo"))
                .andExpect(jsonPath("$.reporter_id").doesNotExist())
                .andExpect(jsonPath("$.handled_by").doesNotExist());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE user_id = ? AND ref_type = 'ADMIN_REPORT' AND type = 'SYSTEM'",
                Integer.class, admin.getUserId()));
    }

    @Test
    @DisplayName("Tạo báo cáo: payload sai → 422 (ma trận lý do, OTHER thiếu mô tả, id/public_id sai chỗ, trường thừa)")
    void createReportValidation() throws Exception {
        String pid = target.getPublicId().toString();
        String[] bad = {
                "{\"target_type\":\"USER\",\"target_public_id\":\"" + pid + "\",\"reason\":\"NO_SHOW\"}",
                "{\"target_type\":\"USER\",\"target_public_id\":\"" + pid + "\",\"reason\":\"OTHER\"}",
                "{\"target_type\":\"USER\",\"target_id\":5,\"reason\":\"SCAM\"}",
                "{\"target_type\":\"PRODUCT\",\"target_public_id\":\"" + pid + "\",\"reason\":\"SCAM\"}",
                "{\"target_type\":\"PRODUCT\",\"reason\":\"SCAM\"}",
                "{\"target_type\":\"USER\",\"target_public_id\":\"" + pid + "\",\"reason\":\"SCAM\",\"foo\":1}",
                "{\"target_type\":\"USER\",\"target_public_id\":\"" + pid + "\",\"reason\":\"BOGUS\"}"
        };
        for (String b : bad) {
            mockMvc.perform(json(post("/api/v1/reports"), reporter).content(b)).andExpect(status().isUnprocessableEntity());
        }
        assertEquals(0, fx.count("reports"));
    }

    @Test
    @DisplayName("Tạo báo cáo: tự báo cáo 400, đối tượng không tồn tại 404, đơn không liên quan 404, trùng đang xử lý 409")
    void createReportRules() throws Exception {
        Category c = fx.category();
        Product own = fx.product(reporter, c, "Của tôi");
        Product ofTarget = fx.product(target, c, "Của target");
        Order order = fx.order(target, other, OrderStatus.PENDING);

        mockMvc.perform(json(post("/api/v1/reports"), reporter).content(
                        "{\"target_type\":\"USER\",\"target_public_id\":\"" + reporter.getPublicId() + "\",\"reason\":\"SCAM\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Không thể tự báo cáo chính mình."));
        mockMvc.perform(json(post("/api/v1/reports"), reporter).content(
                        "{\"target_type\":\"USER\",\"target_public_id\":\"" + java.util.UUID.randomUUID() + "\",\"reason\":\"SCAM\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(json(post("/api/v1/reports"), reporter).content(
                        "{\"target_type\":\"PRODUCT\",\"target_id\":" + own.getProductId() + ",\"reason\":\"SPAM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Không thể báo cáo sản phẩm của chính mình."));
        mockMvc.perform(json(post("/api/v1/reports"), reporter).content(
                        "{\"target_type\":\"PRODUCT\",\"target_id\":999999999,\"reason\":\"SPAM\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Product not found."));
        mockMvc.perform(json(post("/api/v1/reports"), reporter).content(
                        "{\"target_type\":\"ORDER\",\"target_id\":" + order.getOrderId() + ",\"reason\":\"NO_SHOW\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Không tìm thấy đơn hàng."));

        // Sản phẩm HIDDEN: người ngoài không thấy
        jdbc.update("UPDATE products SET status = 'HIDDEN' WHERE product_id = ?", ofTarget.getProductId());
        mockMvc.perform(json(post("/api/v1/reports"), reporter).content(
                        "{\"target_type\":\"PRODUCT\",\"target_id\":" + ofTarget.getProductId() + ",\"reason\":\"SPAM\"}"))
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE products SET status = 'AVAILABLE' WHERE product_id = ?", ofTarget.getProductId());

        String body = "{\"target_type\":\"PRODUCT\",\"target_id\":" + ofTarget.getProductId() + ",\"reason\":\"FAKE_ITEM\"}";
        long id = reportViaApi(reporter, body);
        mockMvc.perform(json(post("/api/v1/reports"), reporter).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Bạn đã gửi báo cáo cho đối tượng này và đang được xử lý."));
        // Sau khi xử lý xong thì được báo cáo lại
        jdbc.update("UPDATE reports SET status = 'REJECTED' WHERE report_id = ?", id);
        reportViaApi(reporter, body);

        // Người tham gia đơn báo cáo được
        Order mine = fx.order(reporter, target, OrderStatus.PENDING);
        reportViaApi(reporter, "{\"target_type\":\"ORDER\",\"target_id\":" + mine.getOrderId() + ",\"reason\":\"NO_SHOW\"}");
    }

    @Test
    @DisplayName("Báo cáo: không token 401; tài khoản BANNED 403")
    void auth() throws Exception {
        mockMvc.perform(post("/api/v1/reports").contentType(MediaType.APPLICATION_JSON).content(userReportBody()))
                .andExpect(status().isUnauthorized());
        jdbc.update("UPDATE users SET status = 'BANNED' WHERE user_id = ?", reporter.getUserId());
        mockMvc.perform(json(post("/api/v1/reports"), reporter).content(userReportBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Bằng chứng: ảnh + video qua MediaStorage, giới hạn 5 ảnh/1 video, tệp sai không upload gì")
    void evidenceUpload() throws Exception {
        long id = reportViaApi(reporter, userReportBody());
        String url = "/api/v1/reports/" + id + "/evidence";
        LocalMediaStorage local = (LocalMediaStorage) mediaStorage;
        long filesBefore;
        try (var s = Files.walk(local.getBaseDir())) {
            filesBefore = s.filter(Files::isRegularFile).count();
        }

        mockMvc.perform(multipart(url)
                        .file(new MockMultipartFile("files", "a.jpg", "image/jpeg", jpeg()))
                        .file(new MockMultipartFile("files", "v.mp4", "video/mp4", mp4()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token(reporter)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.report_id").value(id))
                .andExpect(jsonPath("$.attachments.length()").value(2))
                .andExpect(jsonPath("$.attachments[0].media_type").value("IMAGE"))
                .andExpect(jsonPath("$.attachments[1].media_type").value("VIDEO"))
                .andExpect(jsonPath("$.attachments[1].mime_type").value("video/mp4"));
        assertEquals(2, fx.count("report_attachments"));

        // Video thứ 2 → 409 (đã có 1 video)
        mockMvc.perform(multipart(url).file(new MockMultipartFile("files", "v2.mp4", "video/mp4", mp4()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token(reporter)))
                .andExpect(status().isConflict());

        // Gửi 1 ảnh hợp lệ + 1 tệp sai → 422 và KHÔNG upload tệp nào
        long afterOk;
        try (var s = Files.walk(local.getBaseDir())) {
            afterOk = s.filter(Files::isRegularFile).count();
        }
        mockMvc.perform(multipart(url)
                        .file(new MockMultipartFile("files", "b.jpg", "image/jpeg", jpeg()))
                        .file(new MockMultipartFile("files", "x.txt", "text/plain", "hello world".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token(reporter)))
                .andExpect(status().isUnprocessableEntity());
        try (var s = Files.walk(local.getBaseDir())) {
            assertEquals(afterOk, s.filter(Files::isRegularFile).count());
        }
        assertEquals(filesBefore + 2, afterOk);

        // Thêm tới 5 ảnh rồi ảnh thứ 6 → 409
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(multipart(url).file(new MockMultipartFile("files", "i" + i + ".jpg", "image/jpeg", jpeg()))
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token(reporter)))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(multipart(url).file(new MockMultipartFile("files", "i6.jpg", "image/jpeg", jpeg()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token(reporter)))
                .andExpect(status().isConflict());
        assertEquals(6, fx.count("report_attachments"));
    }

    @Test
    @DisplayName("Bằng chứng: người khác 404 (không lộ tồn tại), báo cáo đã kết thúc 409, thiếu tệp 422")
    void evidenceAccessRules() throws Exception {
        long id = reportViaApi(reporter, userReportBody());
        String url = "/api/v1/reports/" + id + "/evidence";
        MockMultipartFile f = new MockMultipartFile("files", "a.jpg", "image/jpeg", jpeg());

        mockMvc.perform(multipart(url).file(f).header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(multipart(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token(reporter)))
                .andExpect(status().isUnprocessableEntity());
        jdbc.update("UPDATE reports SET status = 'RESOLVED' WHERE report_id = ?", id);
        mockMvc.perform(multipart(url).file(f).header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token(reporter)))
                .andExpect(status().isConflict());
        assertEquals(0, fx.count("report_attachments"));
    }

    @Test
    @DisplayName("Admin xem bằng chứng: STUDENT 403; sai report 404; URL storage không tin cậy bị chặn SSRF (502)")
    void adminEvidenceStreaming() throws Exception {
        long id = reportViaApi(reporter, userReportBody());
        mockMvc.perform(multipart("/api/v1/reports/" + id + "/evidence")
                        .file(new MockMultipartFile("files", "a.jpg", "image/jpeg", jpeg()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token(reporter)))
                .andExpect(status().isCreated());
        long att = jdbc.queryForObject("SELECT attachment_id FROM report_attachments WHERE report_id = ?", Long.class, id);
        String url = "/api/v1/admin/reports/" + id + "/evidence/" + att;

        mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token(reporter)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/reports/" + id + "/evidence/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token(admin)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(url).param("variant", "weird").header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token(admin)))
                .andExpect(status().isUnprocessableEntity());
        // URL giả lập media.local.test không thuộc res.cloudinary.com → 502, không có request mạng nào được gửi
        mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token(admin)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Nguồn lưu trữ tệp không được tin cậy."));
        assertEquals(Arrays.asList(1), Arrays.asList(fx.count("report_attachments")));
    }
}
