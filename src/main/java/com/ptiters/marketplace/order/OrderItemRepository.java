package com.ptiters.marketplace.order;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho OrderItem. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
