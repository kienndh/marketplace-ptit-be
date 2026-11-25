package com.ptiters.marketplace.trackc;

import com.ptiters.marketplace.auction.Auction;
import com.ptiters.marketplace.auction.AuctionRepository;
import com.ptiters.marketplace.auction.AuctionStatus;
import com.ptiters.marketplace.category.Category;
import com.ptiters.marketplace.category.CategoryRepository;
import com.ptiters.marketplace.order.Order;
import com.ptiters.marketplace.order.OrderRepository;
import com.ptiters.marketplace.order.OrderStatus;
import com.ptiters.marketplace.product.*;
import com.ptiters.marketplace.user.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Fixture dùng chung Track C (ghi thật vào PostgreSQL marketplace_java_test_*). */
@Component
public class TrackCFixtures {

    private final UserRepository userRepository;
    private final UserAuthIdentityRepository identityRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final AuctionRepository auctionRepository;
    private final JdbcTemplate jdbc;
    private final Map<Long, String> uidByUserId = new HashMap<>();

    public TrackCFixtures(UserRepository userRepository, UserAuthIdentityRepository identityRepository,
                          CategoryRepository categoryRepository, ProductRepository productRepository,
                          OrderRepository orderRepository, AuctionRepository auctionRepository, JdbcTemplate jdbc) {
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.auctionRepository = auctionRepository;
        this.jdbc = jdbc;
    }

    /** Chặn chạy nhầm DB: chỉ marketplace_java_test hoặc marketplace_java_test_<hậu tố>. */
    public void assertSafeDatabase() {
        String db = jdbc.queryForObject("SELECT current_database()", String.class);
        if (db == null || !db.matches("marketplace_java_test(_[a-z0-9]+)?")) {
            throw new IllegalStateException("Từ chối chạy test trên DB không phải test: " + db);
        }
    }

    public User user(String label, UserRole role) {
        String uid = "trackc_" + label + "_" + UUID.randomUUID().toString().substring(0, 8);
        User u = ProductTestFixtures.createUser(userRepository, uid + "@trackc.test", "Trackc " + label, role);
        ProductTestFixtures.createAuthIdentity(identityRepository, u, uid);
        uidByUserId.put(u.getUserId(), uid);
        return u;
    }

    public String token(User u) {
        return ProductTestFixtures.createToken(uidByUserId.get(u.getUserId()), u.getEmail());
    }

    public Category category() {
        return ProductTestFixtures.createCategory(categoryRepository, "TrackC");
    }

    public Product product(User seller, Category category, String title) {
        return ProductTestFixtures.createProduct(productRepository, seller, category, title,
                new BigDecimal("100000"), ProductStatus.AVAILABLE, ProductApprovalStatus.APPROVED);
    }

    public Order order(User buyer, User seller, OrderStatus status) {
        Order o = Order.builder()
                .orderCode("TC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .buyerId(buyer.getUserId())
                .sellerId(seller.getUserId())
                .status(status)
                .contactName("Người nhận")
                .contactPhone("0900000000")
                .totalAmount(new BigDecimal("100000"))
                .build();
        return orderRepository.saveAndFlush(o);
    }

    public Auction auction(Product product, AuctionStatus status) {
        Auction a = Auction.builder()
                .productId(product.getProductId())
                .startingPrice(new BigDecimal("50000"))
                .bidStep(new BigDecimal("5000"))
                .startsAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(1))
                .endsAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(2))
                .status(status)
                .build();
        return auctionRepository.saveAndFlush(a);
    }

    /** Dọn dữ liệu theo thứ tự FK (chỉ DELETE trên DB test riêng của track). */
    public void cleanAll() {
        assertSafeDatabase();
        for (String t : new String[]{
                "moderation_actions", "user_capability_restrictions", "report_attachments", "reports", "reviews",
                "notifications", "order_status_history", "order_items", "orders", "auction_bids", "auctions",
                "favorites", "product_images", "products", "categories", "user_auth_identities", "users"}) {
            jdbc.update("DELETE FROM " + t);
        }
        uidByUserId.clear();
    }

    public int count(String table) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }
}
