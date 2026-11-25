package com.ptiters.marketplace.conversation;

/** Giá trị khớp 100% với Python / CHECK constraint trong PostgreSQL. */
public enum ConversationInviteStatus {
    PENDING,
    PENDING_APPROVAL,
    ACCEPTED,
    DECLINED,
    CANCELLED
}
