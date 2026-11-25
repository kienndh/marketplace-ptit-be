package com.ptiters.marketplace.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ptiters.marketplace.category.CategorySummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

public record SellerProductResponse(
        @JsonProperty("product_id") Long productId,
        String title,
        String description,
        @JsonProperty("listing_type") String listingType,
        BigDecimal price,
        Integer stock,
        @JsonProperty("reserved_stock") Integer reservedStock,
        @JsonProperty("available_stock") Integer availableStock,
        String status,
        @JsonProperty("moderation_status") String moderationStatus,
        @JsonProperty("approval_status") String approvalStatus,
        @JsonProperty("submitted_for_review_at") Instant submittedForReviewAt,
        @JsonProperty("reviewed_at") Instant reviewedAt,
        @JsonProperty("review_note") String reviewNote,
        @JsonProperty("view_count") Integer viewCount,
        @JsonProperty("thumbnail_url") String thumbnailUrl,
        CategorySummary category,
        SellerAuctionSummary auction,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("updated_at") LocalDateTime updatedAt
) {}
