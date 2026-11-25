package com.ptiters.marketplace.auction;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Domain model representing auction_bids table in PostgreSQL.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuctionBid {
    private Long bidId;
    private Long auctionId;
    private Long bidderId;
    private BigDecimal amount;
    private LocalDateTime bidTime;
}
