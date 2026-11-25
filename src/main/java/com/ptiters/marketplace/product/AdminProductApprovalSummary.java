package com.ptiters.marketplace.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ptiters.marketplace.category.CategorySummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/** Khớp Python {@code AdminProductApprovalSummary} (category/seller lồng nhau, listing_type, image_count). */
public record AdminProductApprovalSummary(
        @JsonProperty("product_id") Long productId,
        String title,
        BigDecimal price,
        @JsonProperty("listing_type") String listingType,
        @JsonProperty("thumbnail_url") String thumbnailUrl,
        CategorySummary category,
        SellerSummary seller,
        @JsonProperty("image_count") int imageCount,
        @JsonProperty("submitted_for_review_at") Instant submittedForReviewAt,
        @JsonProperty("approval_status") String approvalStatus,
        @JsonProperty("created_at") LocalDateTime createdAt
) {}
