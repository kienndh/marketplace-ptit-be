package com.ptiters.marketplace.auction;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Domain model representing auctions table in PostgreSQL.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Auction {
    private Long auctionId;
    private Long productId;
    private BigDecimal startPrice;
    private BigDecimal stepPrice;
    private BigDecimal buyNowPrice;
    private BigDecimal currentPrice;
    private Long winnerId;
    private AuctionStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
