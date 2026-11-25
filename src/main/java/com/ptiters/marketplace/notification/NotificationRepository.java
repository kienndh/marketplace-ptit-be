package com.ptiters.marketplace.notification;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository {
    List<Notification> findByUserId(Long userId, int limit, int offset);
    List<Notification> findUnreadByUserId(Long userId);
    long countUnreadByUserId(Long userId);
    Optional<Notification> findById(Long id);
    void markAllAsRead(Long userId);
    void markAsRead(Long notificationId, Long userId);
    void deleteByIdAndUserId(Long notificationId, Long userId);
    Notification save(Notification notification);
}
