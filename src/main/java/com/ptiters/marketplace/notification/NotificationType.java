package com.ptiters.marketplace.notification;

/** Giá trị khớp 100% với Python / CHECK constraint trong PostgreSQL. */
public enum NotificationType {
    NEW_ORDER,
    ORDER_STATUS,
    NEW_MESSAGE,
    NEW_REVIEW,
    AUCTION_BID,
    AUCTION_OUTBID,
    AUCTION_WON,
    AUCTION_ENDED,
    SYSTEM
}
