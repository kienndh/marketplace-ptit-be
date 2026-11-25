package com.ptiters.marketplace.order;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record OrderItemResponse(
        @JsonProperty("order_item_id") Long orderItemId,
        @JsonProperty("product_id") Long productId,
        @JsonProperty("product_title") String productTitle,
        @JsonProperty("product_image_url") String productImageUrl,
        @JsonProperty("quantity") Integer quantity,
        @JsonProperty("price_at_purchase") BigDecimal priceAtPurchase
) {
}
