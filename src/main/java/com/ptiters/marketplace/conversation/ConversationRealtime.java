package com.ptiters.marketplace.conversation;

import com.ptiters.marketplace.integration.MediaStorage;
import com.ptiters.marketplace.notification.PushNotificationSender;
import com.ptiters.marketplace.realtime.UserEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Điểm phát realtime / push / dọn media của module chat. Mọi tác vụ:
 * <ul>
 *   <li>chạy SAU khi transaction commit (hoặc ngay nếu không có transaction),</li>
 *   <li>best-effort: lỗi chỉ log, không làm hỏng nghiệp vụ (PostgreSQL là nguồn sự thật).</li>
 * </ul>
 */
@Component
public class ConversationRealtime {

    private static final Logger log = LoggerFactory.getLogger(ConversationRealtime.class);

    private final UserEventPublisher userEventPublisher;
    private final PushNotificationSender pushNotificationSender;
    private final MediaStorage mediaStorage;

    public ConversationRealtime(UserEventPublisher userEventPublisher,
                                PushNotificationSender pushNotificationSender,
                                MediaStorage mediaStorage) {
        this.userEventPublisher = userEventPublisher;
        this.pushNotificationSender = pushNotificationSender;
        this.mediaStorage = mediaStorage;
    }

    /** Phát cùng một sự kiện tới nhiều user, sau commit. */
    public void publish(Collection<Long> userIds, Map<String, Object> event) {
        List<Long> targets = List.copyOf(userIds);
        afterCommit(() -> {
            for (Long uid : targets) {
                try {
                    userEventPublisher.publishUserEvent(uid, event);
                } catch (RuntimeException e) {
                    log.warn("Failed to publish {} to user {}: {}", event.get("type"), uid, e.toString());
                }
            }
        });
    }

    /** Web Push tới các user (sau commit). */
    public void push(Collection<Long> userIds, String title, String body, Map<String, String> data) {
        List<Long> targets = List.copyOf(userIds);
        afterCommit(() -> {
            for (Long uid : targets) {
                try {
                    pushNotificationSender.send(uid, title, body, data);
                } catch (RuntimeException e) {
                    log.warn("Failed to dispatch push to user {}: {}", uid, e.toString());
                }
            }
        });
    }

    /** Chạy một tác vụ tuỳ ý sau commit, nuốt lỗi. */
    public void afterCommit(Runnable task) {
        Runnable safe = () -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.warn("Post-commit task failed: {}", e.toString());
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

    /**
     * Nếu transaction hiện tại KHÔNG commit (rollback/lỗi) thì xoá các asset đã upload (best-effort) — tránh rác
     * trên storage khi DB ghi lỗi sau upload. Rác còn sót (storage lỗi) do Track E ({@code MediaCleanupJob}) lo.
     */
    public void destroyAssetsUnlessCommitted(List<UploadedRef> assets) {
        Runnable cleanup = () -> {
            for (UploadedRef ref : assets) {
                try {
                    mediaStorage.destroyAsset(ref.publicId(), ref.resourceType());
                } catch (RuntimeException e) {
                    log.error("Failed to cleanup orphan asset {}: {}", ref.publicId(), e.toString());
                }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        cleanup.run();
                    }
                }
            });
        }
    }

    /** Asset đã upload cần dọn nếu transaction thất bại. */
    public record UploadedRef(String publicId, String resourceType) {
    }

    // ------------------------------------------------------------------------------ dựng payload sự kiện

    static Map<String, Object> messageCreated(Long conversationId, Long messageId, Long senderId) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("type", "MESSAGE_CREATED");
        e.put("conversation_id", conversationId);
        e.put("message_id", messageId);
        e.put("sender_id", senderId);
        return e;
    }

    static Map<String, Object> membershipChanged(Long conversationId, String action) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("type", "GROUP_MEMBERSHIP_CHANGED");
        e.put("conversation_id", conversationId);
        e.put("action", action);
        return e;
    }
}
