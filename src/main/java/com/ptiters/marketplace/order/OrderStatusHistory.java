package com.ptiters.marketplace.order;

import lombok.*;
import java.time.LocalDateTime;

/**
 * Domain model representing order_status_history table in PostgreSQL.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusHistory {
    private Long historyId;
    private Long orderId;
    private OrderStatus fromStatus;
    private OrderStatus toStatus;
    private String note;
    private Long changedByUserId;
    private LocalDateTime createdAt;
}
