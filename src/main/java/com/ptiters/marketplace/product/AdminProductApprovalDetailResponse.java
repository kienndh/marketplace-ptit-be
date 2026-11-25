package com.ptiters.marketplace.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ptiters.marketplace.category.CategorySummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** Khớp Python {@code AdminProductApprovalDetailResponse} (stock tổng, reviewed_by, thumbnail_url; không có view_count). */
public record AdminProductApprovalDetailResponse(
        @JsonProperty("product_id") Long productId,
        String title,
        String description,
        @JsonProperty("listing_type") String listingType,
        BigDecimal price,
        Integer stock,
        String status,
        @JsonProperty("approval_status") String approvalStatus,
        @JsonProperty("moderation_status") String moderationStatus,
        @JsonProperty("submitted_for_review_at") Instant submittedForReviewAt,
        @JsonProperty("reviewed_by") Long reviewedBy,
        @JsonProperty("reviewed_at") Instant reviewedAt,
        @JsonProperty("review_note") String reviewNote,
        @JsonProperty("thumbnail_url") String thumbnailUrl,
        List<ProductImageResponse> images,
        CategorySummary category,
        SellerDetailSummary seller,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("updated_at") LocalDateTime updatedAt
) {}
