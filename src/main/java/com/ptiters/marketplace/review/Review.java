package com.ptiters.marketplace.review;

import lombok.*;
import java.time.LocalDateTime;

/**
 * Domain model representing reviews table in PostgreSQL.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {
    private Long reviewId;
    private Long orderId;
    private Long reviewerId;
    private Long revieweeId;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
