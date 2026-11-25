package com.ptiters.marketplace.notification;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class NotificationQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<Notification> listForUser(
            Long userId,
            int limit,
            LocalDateTime cursorCreatedAt,
            Long cursorNotificationId,
            boolean unreadOnly
    ) {
        StringBuilder jpql = new StringBuilder(
                "SELECT n FROM Notification n WHERE n.userId = :userId "
        );
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);

        if (unreadOnly) {
            jpql.append("AND n.isRead = false ");
        }

        if (cursorCreatedAt != null && cursorNotificationId != null) {
            jpql.append("AND (n.createdAt < :cursorCreatedAt OR (n.createdAt = :cursorCreatedAt AND n.notificationId < :cursorNotificationId)) ");
            params.put("cursorCreatedAt", cursorCreatedAt);
            params.put("cursorNotificationId", cursorNotificationId);
        }

        jpql.append("ORDER BY n.createdAt DESC, n.notificationId DESC");

        TypedQuery<Notification> query = entityManager.createQuery(jpql.toString(), Notification.class);
        params.forEach(query::setParameter);
        query.setMaxResults(limit + 1);
        return query.getResultList();
    }

    public long countUnread(Long userId) {
        Long count = entityManager.createQuery(
                        "SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId AND n.isRead = false",
                        Long.class
                )
                .setParameter("userId", userId)
                .getSingleResult();
        return count != null ? count : 0L;
    }

    public boolean markRead(Long notificationId, Long userId) {
        int updated = entityManager.createQuery(
                        "UPDATE Notification n SET n.isRead = true WHERE n.notificationId = :notificationId AND n.userId = :userId"
                )
                .setParameter("notificationId", notificationId)
                .setParameter("userId", userId)
                .executeUpdate();
        return updated > 0;
    }

    public int markAllRead(Long userId) {
        return entityManager.createQuery(
                        "UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false"
                )
                .setParameter("userId", userId)
                .executeUpdate();
    }

    public boolean deleteUserNotification(Long notificationId, Long userId) {
        int deleted = entityManager.createQuery(
                        "DELETE FROM Notification n WHERE n.notificationId = :notificationId AND n.userId = :userId"
                )
                .setParameter("notificationId", notificationId)
                .setParameter("userId", userId)
                .executeUpdate();
        return deleted > 0;
    }
}
