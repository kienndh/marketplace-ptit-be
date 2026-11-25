package com.ptiters.marketplace.order;

import java.util.List;

/**
 * Repository interface for order_items table (PostgreSQL: order_items).
 */
public interface OrderItemRepository {
    List<OrderItem> findByOrderId(Long orderId);
    OrderItem save(OrderItem item);
}
