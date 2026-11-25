package com.ptiters.marketplace;

import com.ptiters.marketplace.order.CancelledBy;
import com.ptiters.marketplace.order.FulfillmentMethod;
import com.ptiters.marketplace.order.Order;
import com.ptiters.marketplace.order.OrderItem;
import com.ptiters.marketplace.order.OrderRepository;
import com.ptiters.marketplace.order.OrderStatus;
import com.ptiters.marketplace.order.OrderStatusHistory;
import com.ptiters.marketplace.order.PaymentMethod;
import com.ptiters.marketplace.order.PaymentStatus;
import com.ptiters.marketplace.category.Category;
import com.ptiters.marketplace.category.CategoryRepository;
import com.ptiters.marketplace.product.Product;
import com.ptiters.marketplace.product.ProductApprovalStatus;
import com.ptiters.marketplace.product.ProductRepository;
import com.ptiters.marketplace.product.ProductStatus;
import com.ptiters.marketplace.product.ProductTestFixtures;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserRepository;
import com.ptiters.marketplace.user.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bảo chứng mapping Wave 0 với PostgreSQL thật (rollback sau mỗi test). Context load với
 * {@code ddl-auto: validate} đã kiểm tra schema; test này kiểm tra HQL + native enum + cascade hoạt động.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class Wave0EntityMappingIntegrationTest {

    @Autowired
    private EntityManager em;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("Mọi @Entity đều truy vấn HQL được trên bảng thật (kể cả entity tên 'Order')")
    void everyEntityIsQueryable() {
        int checked = 0;
        for (EntityType<?> type : em.getMetamodel().getEntities()) {
            Long count = em.createQuery("select count(e) from " + type.getName() + " e", Long.class).getSingleResult();
            assertTrue(count >= 0);
            checked++;
        }
        assertEquals(33, checked, "7 entity cũ + 26 entity Wave 0 (UserDeviceToken chưa gắn @Entity)");
    }

    @Test
    @DisplayName("Order + OrderItem + OrderStatusHistory: INSERT/SELECT, native enum payment_status_t, cascade")
    void orderAggregateRoundTrip() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User seller = ProductTestFixtures.createUser(userRepository, "w0s_" + suffix + "@synthetic.local", "Seller", UserRole.STUDENT);
        User buyer = ProductTestFixtures.createUser(userRepository, "w0b_" + suffix + "@synthetic.local", "Buyer", UserRole.STUDENT);
        Category category = ProductTestFixtures.createCategory(categoryRepository, "w0cat");
        Product product = ProductTestFixtures.createProduct(productRepository, seller, category, "Máy tính cũ",
                new BigDecimal("1500000"), ProductStatus.AVAILABLE, ProductApprovalStatus.APPROVED);

        Order order = Order.builder()
                .orderCode("W0" + suffix.toUpperCase())
                .buyerId(buyer.getUserId())
                .sellerId(seller.getUserId())
                .contactName("Buyer")
                .contactPhone("0987654321")
                .totalAmount(new BigDecimal("1500000"))
                .build();
        order.getItems().add(OrderItem.builder().order(order).productId(product.getProductId())
                .productTitle("Máy tính cũ").quantity(1).priceAtPurchase(new BigDecimal("1500000")).build());
        order.getStatusHistory().add(OrderStatusHistory.builder().order(order)
                .toStatus(OrderStatus.PENDING).changedBy(buyer.getUserId()).note("created").build());
        Order saved = orderRepository.saveAndFlush(order);
        em.clear();

        Order loaded = em.createQuery("select o from Order o where o.orderCode = :c", Order.class)
                .setParameter("c", "W0" + suffix.toUpperCase()).getSingleResult();
        assertEquals(saved.getOrderId(), loaded.getOrderId());
        assertEquals(OrderStatus.PENDING, loaded.getStatus());
        assertEquals(PaymentMethod.CASH_ON_MEETUP, loaded.getPaymentMethod());
        assertEquals(PaymentStatus.UNPAID, loaded.getPaymentStatus());
        assertEquals(FulfillmentMethod.MEETUP, loaded.getFulfillmentMethod());
        assertEquals(0, new BigDecimal("1500000").compareTo(loaded.getTotalAmount()));
        assertEquals(1, loaded.getItems().size());
        assertEquals(1, loaded.getStatusHistory().size());
        assertEquals(buyer.getUserId(), loaded.getBuyer().getUserId());

        loaded.setPaymentStatus(PaymentStatus.VERIFYING);
        loaded.setPaymentReceiptUrl("https://media.local.test/receipt.png");
        loaded.setStatus(OrderStatus.CANCELLED);
        loaded.setCancelledBy(CancelledBy.SYSTEM);
        em.flush();
        em.clear();
        Order again = orderRepository.findById(saved.getOrderId()).orElseThrow();
        assertEquals(PaymentStatus.VERIFYING, again.getPaymentStatus());
        assertEquals(CancelledBy.SYSTEM, again.getCancelledBy());
    }
}
