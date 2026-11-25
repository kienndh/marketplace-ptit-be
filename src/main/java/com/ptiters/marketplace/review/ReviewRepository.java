package com.ptiters.marketplace.review;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for reviews table (PostgreSQL: reviews).
 */
public interface ReviewRepository {
    Optional<Review> findById(Long reviewId);
    Optional<Review> findByOrderId(Long orderId);
    List<Review> findByRevieweeId(Long revieweeId);
    Review save(Review review);
}
