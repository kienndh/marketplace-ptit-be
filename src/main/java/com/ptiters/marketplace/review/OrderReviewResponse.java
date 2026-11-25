package com.ptiters.marketplace.review;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record OrderReviewResponse(
        @JsonProperty("review_id") Long reviewId,
        @JsonProperty("order_id") Long orderId,
        @JsonProperty("reviewer_id") Long reviewerId,
        @JsonProperty("reviewer_name") String reviewerName,
        Integer rating,
        String comment,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("updated_at") LocalDateTime updatedAt
) {}
