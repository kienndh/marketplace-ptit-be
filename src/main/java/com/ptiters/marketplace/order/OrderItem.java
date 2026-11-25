package com.ptiters.marketplace.order;

import lombok.*;
import java.math.BigDecimal;

/**
 * Domain model representing order_items table in PostgreSQL.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {
    private Long orderItemId;
    private Long orderId;
    private Long productId;
    private String productTitle;
    private String productImageUrl;
    private Integer quantity;
    private BigDecimal priceAtPurchase;
}
