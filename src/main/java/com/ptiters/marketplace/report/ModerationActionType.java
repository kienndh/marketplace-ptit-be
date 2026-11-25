package com.ptiters.marketplace.report;

/** Giá trị khớp 100% với Python / CHECK constraint trong PostgreSQL. */
public enum ModerationActionType {
    USER_BAN,
    USER_UNBAN,
    PRODUCT_HIDE,
    PRODUCT_RESTORE,
    BUY_RESTRICT,
    BUY_RESTORE,
    SELL_RESTRICT,
    SELL_RESTORE
}
