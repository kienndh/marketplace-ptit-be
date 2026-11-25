package com.ptiters.marketplace.notification;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho Notification. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
