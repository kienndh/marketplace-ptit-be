package com.ptiters.marketplace.order;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for orders table (PostgreSQL: orders).
 */
public interface OrderRepository {
    Optional<Order> findById(Long orderId);
    Optional<Order> findByOrderCode(String orderCode);
    List<Order> findByBuyerId(Long buyerId);
    List<Order> findBySellerId(Long sellerId);
    Order save(Order order);
}
