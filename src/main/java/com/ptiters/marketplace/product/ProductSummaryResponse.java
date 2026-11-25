package com.ptiters.marketplace.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ptiters.marketplace.category.CategorySummary;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductSummaryResponse(
        @JsonProperty("product_id") Long productId,
        String title,
        BigDecimal price,
        @JsonProperty("listing_type") String listingType,
        String status,
        @JsonProperty("available_stock") Integer availableStock,
        @JsonProperty("view_count") Integer viewCount,
        @JsonProperty("thumbnail_url") String thumbnailUrl,
        CategorySummary category,
        SellerSummary seller,
        @JsonProperty("created_at") LocalDateTime createdAt
) {}
