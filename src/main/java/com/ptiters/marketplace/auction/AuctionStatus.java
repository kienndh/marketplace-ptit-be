package com.ptiters.marketplace.auction;

/** Giá trị khớp 100% với Python / CHECK constraint trong PostgreSQL. */
public enum AuctionStatus {
    SCHEDULED,
    ACTIVE,
    ENDED,
    CANCELLED
}
