package com.ptiters.marketplace.product;

import com.ptiters.marketplace.category.Category;
import com.ptiters.marketplace.category.CategoryRepository;
import com.ptiters.marketplace.favorite.FavoriteRepository;
import com.ptiters.marketplace.user.*;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminProductApprovalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuthIdentityRepository userAuthIdentityRepository;

    @Autowired
    private DataSource dataSource;

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        @Primary
        public JwtDecoder testJwtDecoder() {
            return ProductTestFixtures.createTestJwtDecoder();
        }
    }

    private User admin;
    private User regularUser;
    private Category category;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();

        admin = ProductTestFixtures.createUser(userRepository, "admin_test@test.com", "Admin Manager", UserRole.ADMIN);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, admin, "admin_test_uid");

        regularUser = ProductTestFixtures.createUser(userRepository, "regular_test@test.com", "Regular User", UserRole.STUDENT);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, regularUser, "regular_test_uid");

        category = ProductTestFixtures.createCategory(categoryRepository, "Đồ điện tử PTIT");
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanDatabase();
    }

    private void cleanDatabase() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM favorites");
            stmt.executeUpdate("DELETE FROM product_images");
            stmt.executeUpdate("DELETE FROM products");
            stmt.executeUpdate("DELETE FROM categories");
            stmt.executeUpdate("DELETE FROM user_capability_restrictions");
            stmt.executeUpdate("DELETE FROM reports");
            stmt.executeUpdate("DELETE FROM user_auth_identities");
            stmt.executeUpdate("DELETE FROM users");
        }
    }

    @Test
    @DisplayName("GET /api/v1/admin/product-approvals - Admin lấy danh sách tin chờ duyệt thành công")
    void testListAdminProductApprovals_AsAdmin() throws Exception {
        Product p = ProductTestFixtures.createProduct(productRepository, regularUser, category, "Máy tính Casio FX580", new BigDecimal("450000"), ProductStatus.AVAILABLE, ProductApprovalStatus.PENDING);
        p.setSubmittedForReviewAt(Instant.now());
        productRepository.save(p);

        String adminToken = ProductTestFixtures.createToken("admin_test_uid", admin.getEmail());

        mockMvc.perform(get("/api/v1/admin/product-approvals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("state", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.items[0].product_id").value(p.getProductId()))
                .andExpect(jsonPath("$.items[0].approval_status").value("PENDING"))
                // Hợp đồng Python/FE: category & seller lồng nhau, có listing_type và image_count
                .andExpect(jsonPath("$.items[0].listing_type").exists())
                .andExpect(jsonPath("$.items[0].category.category_id").exists())
                .andExpect(jsonPath("$.items[0].seller.public_id").exists())
                .andExpect(jsonPath("$.items[0].image_count").isNumber());
    }

    @Test
    @DisplayName("GET /api/v1/admin/product-approvals - Bị chặn 403 khi User thường cố truy cập")
    void testListAdminProductApprovals_AsRegularUser_Forbidden() throws Exception {
        String userToken = ProductTestFixtures.createToken("regular_test_uid", regularUser.getEmail());

        mockMvc.perform(get("/api/v1/admin/product-approvals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Yêu cầu quyền quản trị viên."));
    }

    @Test
    @DisplayName("POST /api/v1/admin/product-approvals/{id}/decision - Admin phê duyệt tin (APPROVE)")
    void testAdminDecision_Approve() throws Exception {
        Product p = ProductTestFixtures.createProduct(productRepository, regularUser, category, "Máy tính Casio FX580", new BigDecimal("450000"), ProductStatus.AVAILABLE, ProductApprovalStatus.PENDING);
        p.setSubmittedForReviewAt(Instant.now());
        productRepository.save(p);

        String adminToken = ProductTestFixtures.createToken("admin_test_uid", admin.getEmail());

        String json = """
                {
                    "decision": "APPROVE"
                }
                """;

        mockMvc.perform(post("/api/v1/admin/product-approvals/" + p.getProductId() + "/decision")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product_id").value(p.getProductId()))
                .andExpect(jsonPath("$.approval_status").value("APPROVED"))
                .andExpect(jsonPath("$.stock").isNumber());

        Product updated = productRepository.findById(p.getProductId()).orElseThrow();
        assertEquals(ProductApprovalStatus.APPROVED, updated.getApprovalStatus());
        assertEquals(admin.getUserId(), updated.getReviewedBy().getUserId());
    }

    @Test
    @DisplayName("POST /api/v1/admin/product-approvals/{id}/decision - Admin từ chối tin kèm lý do (REJECT)")
    void testAdminDecision_Reject_Success() throws Exception {
        Product p = ProductTestFixtures.createProduct(productRepository, regularUser, category, "Hàng giả", new BigDecimal("100000"), ProductStatus.AVAILABLE, ProductApprovalStatus.PENDING);
        p.setSubmittedForReviewAt(Instant.now());
        productRepository.save(p);

        String adminToken = ProductTestFixtures.createToken("admin_test_uid", admin.getEmail());

        String json = """
                {
                    "decision": "REJECT",
                    "note": "Ảnh chụp mờ, không rõ tem chứng nhận của trường"
                }
                """;

        mockMvc.perform(post("/api/v1/admin/product-approvals/" + p.getProductId() + "/decision")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product_id").value(p.getProductId()))
                .andExpect(jsonPath("$.approval_status").value("REJECTED"))
                .andExpect(jsonPath("$.review_note").value("Ảnh chụp mờ, không rõ tem chứng nhận của trường"));

        Product updated = productRepository.findById(p.getProductId()).orElseThrow();
        assertEquals(ProductApprovalStatus.REJECTED, updated.getApprovalStatus());
        assertEquals("Ảnh chụp mờ, không rõ tem chứng nhận của trường", updated.getReviewNote());
    }

    @Test
    @DisplayName("POST /api/v1/admin/product-approvals/{id}/decision - Bị chặn 400 khi từ chối mà để trống lý do")
    void testAdminDecision_Reject_EmptyReason_BadRequest() throws Exception {
        Product p = ProductTestFixtures.createProduct(productRepository, regularUser, category, "Hàng lỗi", new BigDecimal("100000"), ProductStatus.AVAILABLE, ProductApprovalStatus.PENDING);
        p.setSubmittedForReviewAt(Instant.now());
        productRepository.save(p);

        String adminToken = ProductTestFixtures.createToken("admin_test_uid", admin.getEmail());

        String json = """
                {
                    "decision": "REJECT",
                    "note": ""
                }
                """;

        mockMvc.perform(post("/api/v1/admin/product-approvals/" + p.getProductId() + "/decision")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Vui lòng cung cấp lý do từ chối (1..500 ký tự)."));
    }

    @Test
    @DisplayName("POST /api/v1/admin/product-approvals/{id}/decision - Bị chặn 409 khi tin không ở trạng thái PENDING")
    void testAdminDecision_NotPending_Conflict() throws Exception {
        Product p = ProductTestFixtures.createProduct(productRepository, regularUser, category, "Tin nháp", new BigDecimal("100000"), ProductStatus.AVAILABLE, ProductApprovalStatus.DRAFT);

        String adminToken = ProductTestFixtures.createToken("admin_test_uid", admin.getEmail());

        String json = """
                {
                    "decision": "APPROVE"
                }
                """;

        mockMvc.perform(post("/api/v1/admin/product-approvals/" + p.getProductId() + "/decision")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Tin đăng đang ở trạng thái 'DRAFT', không thể xử lý duyệt."));
    }
}
