package com.ptiters.marketplace.review;

import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class ReviewService {

    public OrderReviewResponse createOrderReview(Long orderId, Object currentUser, ReviewCreateRequest payload) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public OrderReviewResponse getOrderReview(Long orderId, Object currentUser) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public OrderReviewResponse updateOrderReview(Long orderId, Object currentUser, ReviewUpdateRequest payload) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public PublicSellerReviewsCursorPage getPublicSellerReviews(UUID sellerPublicId, int limit, String cursor) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }
}
