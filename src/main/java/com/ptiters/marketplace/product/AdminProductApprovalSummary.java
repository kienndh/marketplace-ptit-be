package com.ptiters.marketplace.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record AdminProductApprovalSummary(
        @JsonProperty("product_id") Long productId,
        String title,
        BigDecimal price,
        @JsonProperty("category_id") Integer categoryId,
        @JsonProperty("category_name") String categoryName,
        @JsonProperty("seller_public_id") UUID sellerPublicId,
        @JsonProperty("seller_name") String sellerName,
        @JsonProperty("approval_status") String approvalStatus,
        @JsonProperty("thumbnail_url") String thumbnailUrl,
        @JsonProperty("submitted_for_review_at") Instant submittedForReviewAt,
        @JsonProperty("created_at") LocalDateTime createdAt
) {}
