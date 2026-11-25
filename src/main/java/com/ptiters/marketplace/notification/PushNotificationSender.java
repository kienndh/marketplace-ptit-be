package com.ptiters.marketplace.notification;

import java.util.Map;

/**
 * Gửi Web Push (FCM) tới các thiết bị đã đăng ký của một user.
 * Tương ứng {@code safe_dispatch_push_notification} (Python, notifications/service.py): best-effort, không ném lỗi ra ngoài.
 * Bản mặc định là {@link NoOpPushNotificationSender}; Track E thay bằng bean Firebase thật.
 */
public interface PushNotificationSender {

    void send(Long userId, String title, String body, Map<String, String> data);
}
