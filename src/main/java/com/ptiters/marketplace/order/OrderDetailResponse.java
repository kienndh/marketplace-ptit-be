package com.ptiters.marketplace.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResponse(
        @JsonProperty("order_id") Long orderId,
        @JsonProperty("order_code") String orderCode,
        @JsonProperty("buyer_id") Long buyerId,
        @JsonProperty("buyer_name") String buyerName,
        @JsonProperty("seller_id") Long sellerId,
        @JsonProperty("seller_name") String sellerName,
        String status,
        @JsonProperty("payment_method") String paymentMethod,
        @JsonProperty("payment_status") String paymentStatus,
        @JsonProperty("fulfillment_method") String fulfillmentMethod,
        @JsonProperty("meetup_place") String meetupPlace,
        @JsonProperty("meetup_time") LocalDateTime meetupTime,
        @JsonProperty("shipping_address") String shippingAddress,
        @JsonProperty("contact_name") String contactName,
        @JsonProperty("contact_phone") String contactPhone,
        @JsonProperty("total_amount") BigDecimal totalAmount,
        List<OrderItemResponse> items,
        @JsonProperty("status_history") List<OrderStatusHistoryResponse> statusHistory,
        @JsonProperty("created_at") LocalDateTime createdAt
) {}
