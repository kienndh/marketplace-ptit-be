package com.ptiters.marketplace.notification;

import java.util.Map;

/** Bản mặc định an toàn: không làm gì (không có Firebase credentials). */
public class NoOpPushNotificationSender implements PushNotificationSender {

    @Override
    public void send(Long userId, String title, String body, Map<String, String> data) {
        // no-op
    }
}
