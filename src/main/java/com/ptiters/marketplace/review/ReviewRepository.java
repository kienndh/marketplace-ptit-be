package com.ptiters.marketplace.review;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho Review. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface ReviewRepository extends JpaRepository<Review, Long> {
}
