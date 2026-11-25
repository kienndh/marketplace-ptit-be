package com.ptiters.marketplace.notification;

import com.ptiters.marketplace.realtime.UserEventPublisher;
import com.ptiters.marketplace.user.UserRole;
import com.ptiters.marketplace.user.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Điểm ghi notification DUY NHẤT dùng chung cho mọi track (order, auction, conversation, feedback, ...).
 *
 * <p>Tương ứng {@code NotificationRepository.create} + {@code safe_publish_user_notification} của Python:
 * <ol>
 *   <li>{@link #create}: INSERT + flush trong transaction của caller (KHÔNG commit; caller sở hữu transaction).</li>
 *   <li>Sau khi transaction commit (hoặc ngay lập tức nếu không có transaction): đẩy realtime
 *       {@code NOTIFICATION_CREATED} qua {@link UserEventPublisher} rồi gửi Web Push qua {@link PushNotificationSender}.
 *       Hai bước này best-effort, lỗi chỉ được log, không làm hỏng nghiệp vụ.</li>
 * </ol>
 */
@Component
public class NotificationWriter {

    private static final Logger log = LoggerFactory.getLogger(NotificationWriter.class);

    private final NotificationRepository notificationRepository;
    private final PushNotificationSender pushNotificationSender;
    private final UserEventPublisher userEventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    public NotificationWriter(NotificationRepository notificationRepository,
                              PushNotificationSender pushNotificationSender,
                              UserEventPublisher userEventPublisher) {
        this.notificationRepository = notificationRepository;
        this.pushNotificationSender = pushNotificationSender;
        this.userEventPublisher = userEventPublisher;
    }

    /** Ghi một notification và lên lịch dispatch realtime + push sau commit. */
    public Notification create(Long userId, NotificationType type, String title, String body,
                               String refType, Long refId) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .refType(refType)
                .refId(refId)
                .build();
        Notification saved = notificationRepository.saveAndFlush(notification);
        dispatchAfterCommit(saved);
        return saved;
    }

    /**
     * Tạo SYSTEM notification cho mọi ADMIN đang ACTIVE (giống {@code NotificationService.notify_admins}).
     * Trả về danh sách notification đã tạo (theo thứ tự user_id tăng dần).
     */
    public List<Notification> notifyAdmins(String title, String body, String refType, Long refId) {
        // Tham số hoá enum: user_status là native PostgreSQL enum, không được inline literal vào HQL.
        List<Long> adminIds = entityManager.createQuery(
                        "select u.userId from User u where u.role = :role and u.status = :status order by u.userId asc",
                        Long.class)
                .setParameter("role", UserRole.ADMIN)
                .setParameter("status", UserStatus.ACTIVE)
                .getResultList();
        List<Notification> created = new ArrayList<>(adminIds.size());
        for (Long adminId : adminIds) {
            created.add(create(adminId, NotificationType.SYSTEM, title, body, refType, refId));
        }
        return created;
    }

    /** Payload push giống Python: notification_id, type, ref_type, ref_id (đều là chuỗi). */
    static Map<String, String> pushData(Notification n) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("notification_id", String.valueOf(n.getNotificationId()));
        data.put("type", n.getType().name());
        data.put("ref_type", n.getRefType() == null ? "" : n.getRefType());
        data.put("ref_id", n.getRefId() == null ? "" : String.valueOf(n.getRefId()));
        return data;
    }

    /** Payload realtime giống Python: {"type": "NOTIFICATION_CREATED", "notification": {...}}. */
    static Map<String, Object> realtimeEvent(Notification n) {
        Map<String, Object> notif = new LinkedHashMap<>();
        notif.put("notification_id", n.getNotificationId());
        notif.put("user_id", n.getUserId());
        notif.put("type", n.getType().name());
        notif.put("title", n.getTitle());
        notif.put("body", n.getBody());
        notif.put("ref_type", n.getRefType());
        notif.put("ref_id", n.getRefId());
        notif.put("is_read", n.getIsRead());
        notif.put("created_at", n.getCreatedAt() == null ? null : n.getCreatedAt().toString());
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "NOTIFICATION_CREATED");
        event.put("notification", notif);
        return event;
    }

    private void dispatchAfterCommit(Notification n) {
        Runnable dispatch = () -> {
            try {
                userEventPublisher.publishUserEvent(n.getUserId(), realtimeEvent(n));
            } catch (RuntimeException e) {
                log.warn("Failed to publish NOTIFICATION_CREATED to user {}: {}", n.getUserId(), e.toString());
            }
            try {
                pushNotificationSender.send(n.getUserId(), n.getTitle(),
                        n.getBody() == null ? "" : n.getBody(), pushData(n));
            } catch (RuntimeException e) {
                log.warn("Failed to dispatch push notification to user {}: {}", n.getUserId(), e.toString());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else {
            dispatch.run();
        }
    }
}
