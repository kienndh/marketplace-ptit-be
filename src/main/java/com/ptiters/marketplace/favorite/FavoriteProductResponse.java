package com.ptiters.marketplace.favorite;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ptiters.marketplace.category.CategorySummary;
import com.ptiters.marketplace.product.SellerSummary;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FavoriteProductResponse(
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
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("favorited_at") LocalDateTime favoritedAt
) {}
