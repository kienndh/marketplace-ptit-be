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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerIntegrationTest {

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
    private UserCapabilityRestrictionRepository restrictionRepository;

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

    private User seller;
    private User buyer;
    private User admin;
    private Category category;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();

        seller = ProductTestFixtures.createUser(userRepository, "seller_prod@test.com", "Seller Name", UserRole.STUDENT);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, seller, "seller_prod_uid");

        buyer = ProductTestFixtures.createUser(userRepository, "buyer_prod@test.com", "Buyer Name", UserRole.STUDENT);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, buyer, "buyer_prod_uid");

        admin = ProductTestFixtures.createUser(userRepository, "admin_prod@test.com", "Admin Name", UserRole.ADMIN);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, admin, "admin_prod_uid");

        category = ProductTestFixtures.createCategory(categoryRepository, "Giáo trình PTIT");
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
    @DisplayName("POST /api/v1/products - Tạo sản phẩm thành công với quyền User")
    void testCreateProduct_Success() throws Exception {
        String token = ProductTestFixtures.createToken("seller_prod_uid", seller.getEmail());

        String json = """
                {
                    "category_id": %d,
                    "title": "Sách Lập trình Java Spring Boot",
                    "description": "Sách học viện PTIT rất mới",
                    "price": 75000,
                    "listing_type": "FIXED_PRICE",
                    "stock": 2
                }
                """.formatted(category.getCategoryId());

        mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.product_id").isNumber())
                .andExpect(jsonPath("$.title").value("Sách Lập trình Java Spring Boot"))
                .andExpect(jsonPath("$.price").value(75000))
                .andExpect(jsonPath("$.available_stock").value(2))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.approval_status").value("DRAFT"))
                .andExpect(jsonPath("$.category.category_id").value(category.getCategoryId()))
                .andExpect(jsonPath("$.seller.public_id").value(seller.getPublicId().toString()));
    }

    @Test
    @DisplayName("POST /api/v1/products - Thất bại 404 khi Danh mục không tồn tại")
    void testCreateProduct_CategoryNotFound() throws Exception {
        String token = ProductTestFixtures.createToken("seller_prod_uid", seller.getEmail());

        String json = """
                {
                    "category_id": 999999,
                    "title": "Sách Không Có Danh Mục",
                    "price": 50000
                }
                """;

        mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Category not found."));
    }

    @Test
    @DisplayName("POST /api/v1/products - Thất bại 403 khi tài khoản bị giới hạn quyền SELL")
    void testCreateProduct_SellRestricted() throws Exception {
        long reportId;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            try (java.sql.ResultSet rs = stmt.executeQuery(
                    "INSERT INTO reports (reporter_id, target_type, target_id, reason, status) " +
                    "VALUES (" + admin.getUserId() + ", 'USER', " + seller.getUserId() + ", 'OTHER', 'RESOLVED') RETURNING report_id")) {
                rs.next();
                reportId = rs.getLong(1);
            }
        }

        UserCapabilityRestriction restriction = new UserCapabilityRestriction();
        restriction.setUserId(seller.getUserId());
        restriction.setSourceReportId(reportId);
        restriction.setImposedBy(admin.getUserId());
        restriction.setCapability("SELL");
        restriction.setStartsAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
        restrictionRepository.save(restriction);

        String token = ProductTestFixtures.createToken("seller_prod_uid", seller.getEmail());

        String json = """
                {
                    "category_id": %d,
                    "title": "Sách Bị Hạn Chế Bán",
                    "price": 50000
                }
                """.formatted(category.getCategoryId());

        mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Tài khoản của bạn đang bị giới hạn quyền bán hàng."));
    }

    @Test
    @DisplayName("POST /api/v1/products - Thất bại 401 khi không có Authorization header")
    void testCreateProduct_Unauthenticated() throws Exception {
        String json = """
                {
                    "category_id": %d,
                    "title": "Tin Không Đăng Nhập",
                    "price": 10000
                }
                """.formatted(category.getCategoryId());

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} - Công khai xem chi tiết sản phẩm đã APPROVED")
    void testGetProductDetail_PublicApproved() throws Exception {
        Product p = ProductTestFixtures.createProduct(
                productRepository, seller, category, "Giáo trình CSDL",
                new BigDecimal("60000"), ProductStatus.AVAILABLE, ProductApprovalStatus.APPROVED
        );

        mockMvc.perform(get("/api/v1/products/" + p.getProductId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product_id").value(p.getProductId()))
                .andExpect(jsonPath("$.title").value("Giáo trình CSDL"))
                .andExpect(jsonPath("$.approval_status").value("APPROVED"));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} - Sản phẩm DRAFT bị ẩn 404 đối với người lạ")
    void testGetProductDetail_DraftHiddenFromOthers() throws Exception {
        Product p = ProductTestFixtures.createProduct(
                productRepository, seller, category, "Bản nháp sách CSDL",
                new BigDecimal("60000"), ProductStatus.AVAILABLE, ProductApprovalStatus.DRAFT
        );

        String buyerToken = ProductTestFixtures.createToken("buyer_prod_uid", buyer.getEmail());

        mockMvc.perform(get("/api/v1/products/" + p.getProductId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product not found."));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} - Sản phẩm DRAFT hiển thị 200 OK cho chính chủ sở hữu")
    void testGetProductDetail_DraftVisibleToOwner() throws Exception {
        Product p = ProductTestFixtures.createProduct(
                productRepository, seller, category, "Bản nháp của Seller",
                new BigDecimal("60000"), ProductStatus.AVAILABLE, ProductApprovalStatus.DRAFT
        );

        String sellerToken = ProductTestFixtures.createToken("seller_prod_uid", seller.getEmail());

        mockMvc.perform(get("/api/v1/products/" + p.getProductId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product_id").value(p.getProductId()))
                .andExpect(jsonPath("$.approval_status").value("DRAFT"));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} - Sản phẩm DRAFT hiển thị 200 OK cho Admin")
    void testGetProductDetail_DraftVisibleToAdmin() throws Exception {
        Product p = ProductTestFixtures.createProduct(
                productRepository, seller, category, "Bản nháp để Admin xem",
                new BigDecimal("60000"), ProductStatus.AVAILABLE, ProductApprovalStatus.DRAFT
        );

        String adminToken = ProductTestFixtures.createToken("admin_prod_uid", admin.getEmail());

        mockMvc.perform(get("/api/v1/products/" + p.getProductId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product_id").value(p.getProductId()));
    }

    @Test
    @DisplayName("GET /api/v1/products - Danh sách công khai có lọc và phân trang cursor")
    void testListPublicProducts_FilterAndCursor() throws Exception {
        ProductTestFixtures.createProduct(productRepository, seller, category, "Sách Toán rời rạc 1", new BigDecimal("40000"), ProductStatus.AVAILABLE, ProductApprovalStatus.APPROVED);
        ProductTestFixtures.createProduct(productRepository, seller, category, "Sách Toán rời rạc 2", new BigDecimal("50000"), ProductStatus.AVAILABLE, ProductApprovalStatus.APPROVED);

        mockMvc.perform(get("/api/v1/products")
                        .param("category_id", category.getCategoryId().toString())
                        .param("min_price", "30000")
                        .param("max_price", "60000")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.has_more").value(true))
                .andExpect(jsonPath("$.next_cursor").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/products - Thất bại 422 khi min_price > max_price")
    void testListPublicProducts_InvalidPriceRange() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("min_price", "100000")
                        .param("max_price", "50000"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("min_price cannot be greater than max_price."));
    }

    @Test
    @DisplayName("GET /api/v1/products - Thất bại 422 khi cursor không hợp lệ")
    void testListPublicProducts_InvalidCursor() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("cursor", "malformed_cursor_base64_string"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Invalid pagination cursor."));
    }

    @Test
    @DisplayName("GET /api/v1/products/me - Lấy danh sách tin bán của chính người dùng")
    void testListMyProducts() throws Exception {
        Product p = ProductTestFixtures.createProduct(productRepository, seller, category, "Sách của Seller", new BigDecimal("50000"), ProductStatus.AVAILABLE, ProductApprovalStatus.DRAFT);
        String token = ProductTestFixtures.createToken("seller_prod_uid", seller.getEmail());

        mockMvc.perform(get("/api/v1/products/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.items[0].product_id").value(p.getProductId()));
    }

    @Test
    @DisplayName("PATCH /api/v1/products/{id} - Chủ sở hữu cập nhật tin đăng thành công")
    void testUpdateProduct_Success() throws Exception {
        Product p = ProductTestFixtures.createProduct(productRepository, seller, category, "Tên cũ", new BigDecimal("50000"), ProductStatus.AVAILABLE, ProductApprovalStatus.DRAFT);
        String token = ProductTestFixtures.createToken("seller_prod_uid", seller.getEmail());

        String json = """
                {
                    "title": "Tên mới đã cập nhật",
                    "price": 65000
                }
                """;

        mockMvc.perform(patch("/api/v1/products/" + p.getProductId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Tên mới đã cập nhật"))
                .andExpect(jsonPath("$.price").value(65000));
    }

    @Test
    @DisplayName("PATCH /api/v1/products/{id} - Bị chặn 403 khi người khác cố sửa tin")
    void testUpdateProduct_ForbiddenNonOwner() throws Exception {
        Product p = ProductTestFixtures.createProduct(productRepository, seller, category, "Sách của Seller", new BigDecimal("50000"), ProductStatus.AVAILABLE, ProductApprovalStatus.DRAFT);
        String buyerToken = ProductTestFixtures.createToken("buyer_prod_uid", buyer.getEmail());

        String json = """
                {
                    "title": "Hacker sửa tiêu đề"
                }
                """;

        mockMvc.perform(patch("/api/v1/products/" + p.getProductId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Chỉ người bán sở hữu tin đăng mới có quyền chỉnh sửa."));
    }

    @Test
    @DisplayName("DELETE /api/v1/products/{id} - Chủ sở hữu xóa mềm tin thành công (204 No Content)")
    void testSoftDeleteProduct_Success() throws Exception {
        Product p = ProductTestFixtures.createProduct(productRepository, seller, category, "Sách sắp xóa", new BigDecimal("50000"), ProductStatus.AVAILABLE, ProductApprovalStatus.DRAFT);
        String token = ProductTestFixtures.createToken("seller_prod_uid", seller.getEmail());

        mockMvc.perform(delete("/api/v1/products/" + p.getProductId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        Product deleted = productRepository.findById(p.getProductId()).orElseThrow();
        assertEquals(true, deleted.getIsDeleted());
        assertEquals(ProductStatus.HIDDEN, deleted.getStatus());
    }

    @Test
    @DisplayName("DELETE /api/v1/products/{id} - Bị chặn 403 khi người khác cố xóa tin")
    void testSoftDeleteProduct_ForbiddenNonOwner() throws Exception {
        Product p = ProductTestFixtures.createProduct(productRepository, seller, category, "Sách của Seller", new BigDecimal("50000"), ProductStatus.AVAILABLE, ProductApprovalStatus.DRAFT);
        String buyerToken = ProductTestFixtures.createToken("buyer_prod_uid", buyer.getEmail());

        mockMvc.perform(delete("/api/v1/products/" + p.getProductId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Chỉ người bán sở hữu tin đăng mới có quyền xóa."));
    }

    @Test
    @DisplayName("POST /api/v1/products/{id}/submit-review - Gửi duyệt thành công khi có ảnh (approval_status PENDING)")
    void testSubmitProductReview_Success() throws Exception {
        Product p = ProductTestFixtures.createProduct(productRepository, seller, category, "Sách chuẩn bị gửi duyệt", new BigDecimal("50000"), ProductStatus.AVAILABLE, ProductApprovalStatus.DRAFT);
        ProductImage img = ProductImage.builder()
                .product(p)
                .imageUrl("https://res.cloudinary.com/synthetic/image/upload/v1/img1.jpg")
                .sortOrder((short) 0)
                .isThumbnail(true)
                .build();
        productImageRepository.save(img);

        String token = ProductTestFixtures.createToken("seller_prod_uid", seller.getEmail());

        mockMvc.perform(post("/api/v1/products/" + p.getProductId() + "/submit-review")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("PENDING"))
                .andExpect(jsonPath("$.submitted_for_review_at").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/products/{id}/submit-review - Bị chặn 409 khi tin đăng chưa có ảnh")
    void testSubmitProductReview_NoImages_Conflict() throws Exception {
        Product p = ProductTestFixtures.createProduct(productRepository, seller, category, "Sách không có ảnh", new BigDecimal("50000"), ProductStatus.AVAILABLE, ProductApprovalStatus.DRAFT);
        String token = ProductTestFixtures.createToken("seller_prod_uid", seller.getEmail());

        mockMvc.perform(post("/api/v1/products/" + p.getProductId() + "/submit-review")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Tin đăng cần có ít nhất 1 hình ảnh trước khi gửi duyệt."));
    }

    @Test
    @DisplayName("POST /api/v1/products/{id}/view - Đếm view chính xác với 24h deduplication")
    void testRegisterProductView() throws Exception {
        Product p = ProductTestFixtures.createProduct(productRepository, seller, category, "Sách kiểm tra view", new BigDecimal("50000"), ProductStatus.AVAILABLE, ProductApprovalStatus.APPROVED);

        UUID anonId = UUID.randomUUID();
        String json = "{\"anonymous_viewer_id\": \"" + anonId + "\"}";

        // Lần 1: Được tính view (counted = true)
        mockMvc.perform(post("/api/v1/products/" + p.getProductId() + "/view")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.view_count").value(1))
                .andExpect(jsonPath("$.counted").value(true));

        // Lần 2 (trong 24h): Trùng lặp, không tăng view (counted = false)
        mockMvc.perform(post("/api/v1/products/" + p.getProductId() + "/view")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.view_count").value(1))
                .andExpect(jsonPath("$.counted").value(false));
    }

    @Test
    @DisplayName("PATCH & PUT /images - Đổi thumbnail và sắp xếp lại thứ tự ảnh")
    void testSetThumbnailAndReorderImages() throws Exception {
        Product p = ProductTestFixtures.createProduct(productRepository, seller, category, "Sách có nhiều ảnh", new BigDecimal("50000"), ProductStatus.AVAILABLE, ProductApprovalStatus.DRAFT);
        ProductImage img1 = productImageRepository.save(ProductImage.builder().product(p).imageUrl("https://cloud/1.jpg").sortOrder((short) 0).isThumbnail(true).build());
        ProductImage img2 = productImageRepository.save(ProductImage.builder().product(p).imageUrl("https://cloud/2.jpg").sortOrder((short) 1).isThumbnail(false).build());

        String token = ProductTestFixtures.createToken("seller_prod_uid", seller.getEmail());

        // Đổi img2 thành thumbnail
        mockMvc.perform(patch("/api/v1/products/" + p.getProductId() + "/images/" + img2.getImageId() + "/thumbnail")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Thumbnail updated successfully."));

        // Sắp xếp lại thứ tự
        String reorderJson = """
                {
                    "images": [
                        {"image_id": %d, "sort_order": 0},
                        {"image_id": %d, "sort_order": 1}
                    ]
                }
                """.formatted(img2.getImageId(), img1.getImageId());

        mockMvc.perform(put("/api/v1/products/" + p.getProductId() + "/images/reorder")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reorderJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Images reordered successfully."));
    }

    @Test
    @DisplayName("POST /api/v1/products/{id}/images/upload - Lưu ảnh qua MediaStorage, ảnh đầu là thumbnail, duyệt cũ về DRAFT")
    void testUploadProductImage_StoresViaMediaStorageAndInvalidatesApproval() throws Exception {
        Product p = ProductTestFixtures.createProduct(productRepository, seller, category, "Sách upload ảnh", new BigDecimal("50000"), ProductStatus.AVAILABLE, ProductApprovalStatus.APPROVED);
        String token = ProductTestFixtures.createToken("seller_prod_uid", seller.getEmail());
        byte[] png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAoAAAAKCAYAAACNMs+9AAAAFUlEQVR42mP8z8BQz0AEYBxVSF+FABJADveWkH6oAAAAAElFTkSuQmCC");
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile("file", "a.png", "image/png", png);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/products/" + p.getProductId() + "/images/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.image_url").value(org.hamcrest.Matchers.startsWith("https://media.local.test/")))
                .andExpect(jsonPath("$.is_thumbnail").value(true))
                .andExpect(jsonPath("$.sort_order").value(0));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/products/" + p.getProductId() + "/images/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.is_thumbnail").value(false))
                .andExpect(jsonPath("$.sort_order").value(1));

        Product reloaded = productRepository.findById(p.getProductId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(ProductApprovalStatus.DRAFT, reloaded.getApprovalStatus());
    }
}
