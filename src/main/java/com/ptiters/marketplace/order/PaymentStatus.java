package com.ptiters.marketplace.order;

/** Giá trị khớp 100% với Python / CHECK constraint trong PostgreSQL. */
public enum PaymentStatus {
    UNPAID,
    VERIFYING,
    PAID
}
