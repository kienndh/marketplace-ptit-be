package com.ptiters.marketplace.review;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/** Khớp {@code OrderReviewResponse} của Python (API_CONTRACT mục 8.1). */
public record OrderReviewResponse(
        @JsonProperty("review_id") Long reviewId,
        @JsonProperty("order_id") Long orderId,
        @JsonProperty("reviewer_id") Long reviewerId,
        @JsonProperty("reviewee_id") Long revieweeId,
        Integer rating,
        String comment,
        @JsonProperty("reviewer_name") String reviewerName,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("updated_at") LocalDateTime updatedAt
) {}
