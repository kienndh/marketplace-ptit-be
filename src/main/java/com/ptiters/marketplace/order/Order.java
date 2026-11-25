package com.ptiters.marketplace.order;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Domain model representing orders table in PostgreSQL.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    private Long orderId;
    private String orderCode;
    private Long buyerId;
    private Long sellerId;
    private OrderStatus status;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private String paymentReceiptUrl;
    private LocalDateTime receiptUploadedAt;
    private LocalDateTime paidConfirmedAt;
    private String paymentNote;
    private FulfillmentMethod fulfillmentMethod;
    private String meetupPlace;
    private LocalDateTime meetupTime;
    private String shippingAddress;
    private String contactName;
    private String contactPhone;
    private String buyerNote;
    private BigDecimal totalAmount;
    private LocalDateTime autoCancelAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private CancelledBy cancelledBy;
    private String cancelReason;
    private String paymentBankBin;
    private String paymentBankAccountNumber;
    private String paymentBankAccountName;
    private Long auctionId;
    private LocalDateTime shippingExpectedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<OrderItem> items;
}
