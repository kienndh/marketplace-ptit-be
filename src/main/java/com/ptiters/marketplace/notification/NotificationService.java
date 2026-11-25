package com.ptiters.marketplace.notification;

import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    public PushSubscriptionResponse subscribePushToken(Long userId, PushSubscribeRequest request) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public PushSubscriptionResponse unsubscribePushToken(Long userId, PushUnsubscribeRequest request) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public NotificationListResponse listNotifications(Long userId, int limit, int offset) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public UnreadCountResponse getUnreadCount(Long userId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public void markAllRead(Long userId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public void markRead(Long userId, Long notificationId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public void deleteNotification(Long userId, Long notificationId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }
}
