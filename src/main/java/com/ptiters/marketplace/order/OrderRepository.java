package com.ptiters.marketplace.order;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho Order. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {
}
