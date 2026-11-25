package com.ptiters.marketplace.order;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Python: {@code OrderSummaryResponse} (phần tử của {@code OrderCursorPage.items}). */
public record OrderSummaryResponse(
        @JsonProperty("order_id") Long orderId,
        @JsonProperty("order_code") String orderCode,
        @JsonProperty("buyer_id") Long buyerId,
        @JsonProperty("seller_id") Long sellerId,
        @JsonProperty("status") String status,
        @JsonProperty("payment_method") String paymentMethod,
        @JsonProperty("payment_status") String paymentStatus,
        @JsonProperty("fulfillment_method") String fulfillmentMethod,
        @JsonProperty("total_amount") BigDecimal totalAmount,
        @JsonProperty("item") OrderItemResponse item,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("updated_at") LocalDateTime updatedAt
) {
}
