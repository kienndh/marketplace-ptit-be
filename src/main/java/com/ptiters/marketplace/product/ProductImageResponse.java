package com.ptiters.marketplace.product;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProductImageResponse(
        @JsonProperty("image_id") Long imageId,
        @JsonProperty("product_id") Long productId,
        @JsonProperty("image_url") String imageUrl,
        @JsonProperty("detail_url") String detailUrl,
        @JsonProperty("sort_order") Short sortOrder,
        @JsonProperty("is_thumbnail") Boolean isThumbnail
) {}
