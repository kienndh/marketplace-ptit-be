package com.ptiters.marketplace.order;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Python: {@code OrderDetailResponse}. */
public record OrderDetailResponse(
        @JsonProperty("order_id") Long orderId,
        @JsonProperty("order_code") String orderCode,
        @JsonProperty("buyer_id") Long buyerId,
        @JsonProperty("buyer_public_id") UUID buyerPublicId,
        @JsonProperty("buyer_name") String buyerName,
        @JsonProperty("buyer_avatar_url") String buyerAvatarUrl,
        @JsonProperty("seller_id") Long sellerId,
        @JsonProperty("seller_public_id") UUID sellerPublicId,
        @JsonProperty("seller_name") String sellerName,
        @JsonProperty("seller_avatar_url") String sellerAvatarUrl,
        @JsonProperty("status") String status,
        @JsonProperty("payment_method") String paymentMethod,
        @JsonProperty("payment_status") String paymentStatus,
        @JsonProperty("payment_receipt_url") String paymentReceiptUrl,
        @JsonProperty("receipt_uploaded_at") LocalDateTime receiptUploadedAt,
        @JsonProperty("paid_confirmed_at") LocalDateTime paidConfirmedAt,
        @JsonProperty("payment_note") String paymentNote,
        @JsonProperty("payment_bank") OrderPaymentBankResponse paymentBank,
        @JsonProperty("fulfillment_method") String fulfillmentMethod,
        @JsonProperty("meetup_place") String meetupPlace,
        @JsonProperty("meetup_time") LocalDateTime meetupTime,
        @JsonProperty("shipping_address") String shippingAddress,
        @JsonProperty("shipping_expected_at") LocalDateTime shippingExpectedAt,
        @JsonProperty("contact_name") String contactName,
        @JsonProperty("contact_phone") String contactPhone,
        @JsonProperty("buyer_note") String buyerNote,
        @JsonProperty("total_amount") BigDecimal totalAmount,
        @JsonProperty("auto_cancel_at") LocalDateTime autoCancelAt,
        @JsonProperty("confirmed_at") LocalDateTime confirmedAt,
        @JsonProperty("completed_at") LocalDateTime completedAt,
        @JsonProperty("cancelled_at") LocalDateTime cancelledAt,
        @JsonProperty("cancelled_by") String cancelledBy,
        @JsonProperty("cancel_reason") String cancelReason,
        @JsonProperty("items") List<OrderItemResponse> items,
        @JsonProperty("status_history") List<OrderStatusHistoryResponse> statusHistory,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("updated_at") LocalDateTime updatedAt
) {
}
