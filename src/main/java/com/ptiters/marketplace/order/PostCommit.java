package com.ptiters.marketplace.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Chạy tác vụ realtime/push SAU khi transaction commit, best-effort (lỗi chỉ log).
 * Rollback thì không chạy. Không có transaction đang hoạt động thì chạy ngay.
 * Tương ứng khối {@code try: await publish_*(...) except Exception: logger.error(...)} sau {@code commit()} của Python.
 */
public final class PostCommit {

    private static final Logger log = LoggerFactory.getLogger(PostCommit.class);

    private PostCommit() {
    }

    public static void run(String what, Runnable action) {
        Runnable safe = () -> {
            try {
                action.run();
            } catch (RuntimeException e) {
                log.warn("Post-commit dispatch '{}' failed: {}", what, e.toString());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safe.run();
                }
            });
        } else {
            safe.run();
        }
    }
}
