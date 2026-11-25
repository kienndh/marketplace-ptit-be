package com.ptiters.marketplace.favorite;

import com.ptiters.marketplace.category.Category;
import com.ptiters.marketplace.category.CategoryRepository;
import com.ptiters.marketplace.product.*;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FavoriteControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

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

    private User buyer;
    private User seller;
    private Category category;
    private Product approvedProduct;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();

        buyer = ProductTestFixtures.createUser(userRepository, "buyer_fav@test.com", "Buyer Fav", UserRole.STUDENT);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, buyer, "buyer_fav_uid");

        seller = ProductTestFixtures.createUser(userRepository, "seller_fav@test.com", "Seller Fav", UserRole.STUDENT);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, seller, "seller_fav_uid");

        category = ProductTestFixtures.createCategory(categoryRepository, "Văn phòng phẩm PTIT");

        approvedProduct = ProductTestFixtures.createProduct(
                productRepository, seller, category, "Bộ compa PTIT",
                new BigDecimal("25000"), ProductStatus.AVAILABLE, ProductApprovalStatus.APPROVED
        );
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
    @DisplayName("POST /api/v1/favorites/{productId} - Thêm yêu thích thành công (204 No Content)")
    void testAddFavorite_Success() throws Exception {
        String token = ProductTestFixtures.createToken("buyer_fav_uid", buyer.getEmail());

        mockMvc.perform(post("/api/v1/favorites/" + approvedProduct.getProductId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        assertTrue(favoriteRepository.existsByUserIdAndProductId(buyer.getUserId(), approvedProduct.getProductId()));
    }

    @Test
    @DisplayName("POST /api/v1/favorites/{productId} - Thêm lại lần 2 vẫn thành công (Idempotent 204 No Content)")
    void testAddFavorite_Idempotent() throws Exception {
        String token = ProductTestFixtures.createToken("buyer_fav_uid", buyer.getEmail());

        // Lần 1
        mockMvc.perform(post("/api/v1/favorites/" + approvedProduct.getProductId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        // Lần 2 (idempotent)
        mockMvc.perform(post("/api/v1/favorites/" + approvedProduct.getProductId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        assertTrue(favoriteRepository.existsByUserIdAndProductId(buyer.getUserId(), approvedProduct.getProductId()));
    }

    @Test
    @DisplayName("POST /api/v1/favorites/{productId} - Thất bại 404 khi sản phẩm chưa APPROVED hoặc không AVAILABLE")
    void testAddFavorite_ProductNotAvailable_NotFound() throws Exception {
        Product draftProduct = ProductTestFixtures.createProduct(
                productRepository, seller, category, "Sách bản nháp",
                new BigDecimal("30000"), ProductStatus.AVAILABLE, ProductApprovalStatus.DRAFT
        );

        String token = ProductTestFixtures.createToken("buyer_fav_uid", buyer.getEmail());

        mockMvc.perform(post("/api/v1/favorites/" + draftProduct.getProductId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product not found."));
    }

    @Test
    @DisplayName("GET /api/v1/favorites - Lấy danh sách sản phẩm đã yêu thích có phân trang")
    void testListFavorites() throws Exception {
        favoriteServiceAdd(buyer.getUserId(), approvedProduct.getProductId());
        String token = ProductTestFixtures.createToken("buyer_fav_uid", buyer.getEmail());

        mockMvc.perform(get("/api/v1/favorites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].product_id").value(approvedProduct.getProductId()))
                .andExpect(jsonPath("$.items[0].title").value(approvedProduct.getTitle()))
                .andExpect(jsonPath("$.has_more").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/favorites/ids - Lấy danh sách ID sản phẩm đã yêu thích")
    void testGetFavoriteIds() throws Exception {
        favoriteServiceAdd(buyer.getUserId(), approvedProduct.getProductId());
        String token = ProductTestFixtures.createToken("buyer_fav_uid", buyer.getEmail());

        mockMvc.perform(get("/api/v1/favorites/ids")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product_ids", hasSize(1)))
                .andExpect(jsonPath("$.product_ids[0]").value(approvedProduct.getProductId()));
    }

    @Test
    @DisplayName("DELETE /api/v1/favorites/{productId} - Bỏ lưu yêu thích thành công (204 No Content)")
    void testRemoveFavorite_Success() throws Exception {
        favoriteServiceAdd(buyer.getUserId(), approvedProduct.getProductId());
        String token = ProductTestFixtures.createToken("buyer_fav_uid", buyer.getEmail());

        mockMvc.perform(delete("/api/v1/favorites/" + approvedProduct.getProductId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        assertEquals(false, favoriteRepository.existsByUserIdAndProductId(buyer.getUserId(), approvedProduct.getProductId()));
    }

    @Test
    @DisplayName("DELETE /api/v1/favorites/{productId} - Bỏ lưu khi chưa từng yêu thích vẫn 204 No Content (Idempotent)")
    void testRemoveFavorite_Idempotent() throws Exception {
        String token = ProductTestFixtures.createToken("buyer_fav_uid", buyer.getEmail());

        mockMvc.perform(delete("/api/v1/favorites/" + approvedProduct.getProductId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST & GET /api/v1/favorites - Bị chặn 401 khi không đăng nhập")
    void testFavorite_Unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/favorites"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/favorites/" + approvedProduct.getProductId()))
                .andExpect(status().isUnauthorized());
    }

    private void favoriteServiceAdd(Long userId, Long productId) {
        Favorite fav = Favorite.builder()
                .userId(userId)
                .productId(productId)
                .user(buyer)
                .product(approvedProduct)
                .build();
        favoriteRepository.save(fav);
    }
}
