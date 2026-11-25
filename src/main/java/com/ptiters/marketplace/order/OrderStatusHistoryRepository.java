package com.ptiters.marketplace.order;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho OrderStatusHistory. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {
}
