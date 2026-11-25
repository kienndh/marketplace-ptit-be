package com.ptiters.marketplace.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderSummaryResponse(
        @JsonProperty("order_id") Long orderId,
        @JsonProperty("order_code") String orderCode,
        String status,
        @JsonProperty("payment_status") String paymentStatus,
        @JsonProperty("total_amount") BigDecimal totalAmount,
        @JsonProperty("product_title") String productTitle,
        @JsonProperty("product_image_url") String productImageUrl,
        @JsonProperty("created_at") LocalDateTime createdAt
) {}
