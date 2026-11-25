package com.ptiters.marketplace.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Chạy tác dụng phụ (realtime, thu hồi phiên) SAU khi transaction commit, best-effort:
 * lỗi chỉ được log, không làm hỏng nghiệp vụ; rollback thì không chạy.
 */
final class AfterCommit {

    private static final Logger log = LoggerFactory.getLogger(AfterCommit.class);

    private AfterCommit() {}

    static void run(String description, Runnable action) {
        Runnable safe = () -> {
            try {
                action.run();
            } catch (RuntimeException e) {
                log.warn("Post-commit side effect failed ({}): {}", description, e.toString());
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
