package com.ptiters.marketplace.order;

import com.ptiters.marketplace.category.Category;
import com.ptiters.marketplace.category.CategoryRepository;
import com.ptiters.marketplace.product.Product;
import com.ptiters.marketplace.product.ProductApprovalStatus;
import com.ptiters.marketplace.product.ProductRepository;
import com.ptiters.marketplace.product.ProductStatus;
import com.ptiters.marketplace.product.ProductTestFixtures;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserAuthIdentityRepository;
import com.ptiters.marketplace.user.UserRepository;
import com.ptiters.marketplace.user.UserRole;
import com.ptiters.marketplace.auction.AuctionRepository;
import com.ptiters.marketplace.user.UserCapabilityRestriction;
import com.ptiters.marketplace.user.UserCapabilityRestrictionRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tiện ích dùng chung cho test tích hợp Track A (PostgreSQL thật, DB `marketplace_java_test*`). */
public abstract class TrackATestSupport {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UserRepository userRepository;
    @Autowired protected UserAuthIdentityRepository identityRepository;
    @Autowired protected CategoryRepository categoryRepository;
    @Autowired protected ProductRepository productRepository;
    @Autowired protected OrderRepository orderRepository;
    @Autowired protected AuctionRepository auctionRepository;
    @Autowired protected UserCapabilityRestrictionRepository restrictionRepository;

    /** Tất cả bảng test động tới (thứ tự an toàn FK). */
    private static final String[] TABLES = {
            "notifications", "order_status_history", "order_items", "orders", "auction_bids", "auctions",
            "favorites", "product_images", "products", "categories", "user_capability_restrictions",
            "reports", "user_auth_identities", "users"
    };

    protected void assertTestDatabase() {
        String db = jdbc.queryForObject("SELECT current_database()", String.class);
        assertTrue(db != null && db.matches("marketplace_java_test(_[a-z0-9]+)?"),
                "Chỉ được chạy trên marketplace_java_test*, nhưng đang là: " + db);
    }

    protected void cleanDatabase() {
        assertTestDatabase();
        for (String t : TABLES) {
            jdbc.update("DELETE FROM " + t);
        }
    }

    protected void assertAllTablesEmpty() {
        for (String t : TABLES) {
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM " + t, Integer.class), "Bảng " + t + " phải rỗng");
        }
    }

    protected User newUser(String key) {
        User u = ProductTestFixtures.createUser(userRepository, key + "@trackA.test", "User " + key, UserRole.STUDENT);
        ProductTestFixtures.createAuthIdentity(identityRepository, u, key + "_uid");
        return u;
    }

    protected String token(User u) {
        String uid = jdbc.queryForObject("SELECT provider_uid FROM user_auth_identities WHERE user_id = ?", String.class,
                u.getUserId());
        return ProductTestFixtures.createToken(uid, u.getEmail());
    }

    protected Category newCategory() {
        return ProductTestFixtures.createCategory(categoryRepository, "TrackA Category");
    }

    protected Product newProduct(User seller, Category c, String title, String price, int stock) {
        Product p = ProductTestFixtures.createProduct(productRepository, seller, c, title, new BigDecimal(price),
                ProductStatus.AVAILABLE, ProductApprovalStatus.APPROVED);
        p.setStock(stock);
        return productRepository.save(p);
    }

    protected <T extends AbstractMockHttpServletRequestBuilder<T>> T auth(T b, User u) {
        return b.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(u));
    }

    protected int stock(Long productId) {
        return jdbc.queryForObject("SELECT stock FROM products WHERE product_id = ?", Integer.class, productId);
    }

    protected int reserved(Long productId) {
        return jdbc.queryForObject("SELECT reserved_stock FROM products WHERE product_id = ?", Integer.class, productId);
    }

    protected int notificationCount(Long userId, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = ?", Integer.class,
                userId, type);
    }

    // ---- chỉnh thời gian QUA JPA (không dùng now() của SQL): nhất quán với cách Hibernate ghi LocalDateTime/UTC,
    // ---- không phụ thuộc múi giờ mặc định của JVM chạy test.

    protected static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    protected void expireOrderAutoCancel(long orderId) {
        Order o = orderRepository.findById(orderId).orElseThrow();
        o.setAutoCancelAt(utcNow().minusMinutes(1));
        orderRepository.save(o);
    }

    protected void restrict(User target, String capability, User imposedBy) {
        Long reportId = jdbc.queryForObject("INSERT INTO reports(reporter_id, target_type, target_id, reason) "
                + "VALUES (?, 'USER', ?, 'SCAM') RETURNING report_id", Long.class, imposedBy.getUserId(), target.getUserId());
        UserCapabilityRestriction r = new UserCapabilityRestriction();
        r.setUserId(target.getUserId());
        r.setSourceReportId(reportId);
        r.setImposedBy(imposedBy.getUserId());
        r.setCapability(capability);
        r.setStartsAt(utcNow().minusHours(1));
        restrictionRepository.save(r);
    }

    protected void expireAuction(long auctionId) {
        com.ptiters.marketplace.auction.Auction a = auctionRepository.findById(auctionId).orElseThrow();
        a.setStartsAt(utcNow().minusHours(2));
        a.setEndsAt(utcNow().minusHours(1));
        auctionRepository.save(a);
    }

    protected void expireWinnerDeadline(long auctionId) {
        com.ptiters.marketplace.auction.Auction a = auctionRepository.findById(auctionId).orElseThrow();
        a.setWinnerCheckoutDeadline(utcNow().minusMinutes(1));
        auctionRepository.save(a);
    }
}
