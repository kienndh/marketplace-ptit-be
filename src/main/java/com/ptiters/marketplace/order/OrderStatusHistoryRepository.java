package com.ptiters.marketplace.order;

import java.util.List;

/**
 * Repository interface for order_status_history table (PostgreSQL: order_status_history).
 */
public interface OrderStatusHistoryRepository {
    List<OrderStatusHistory> findByOrderId(Long orderId);
    OrderStatusHistory save(OrderStatusHistory history);
}
