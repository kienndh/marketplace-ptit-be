package com.ptiters.marketplace.review;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record PublicSellerReviewItem(
        @JsonProperty("review_id") Long reviewId,
        @JsonProperty("reviewer_name") String reviewerName,
        @JsonProperty("reviewer_avatar") String reviewerAvatar,
        Integer rating,
        String comment,
        @JsonProperty("created_at") LocalDateTime createdAt
) {}
