package com.ptiters.marketplace.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record NotificationResponse(
        @JsonProperty("notification_id") Long notificationId,
        String type,
        String title,
        String body,
        @JsonProperty("ref_type") String refType,
        @JsonProperty("ref_id") Long refId,
        @JsonProperty("is_read") Boolean isRead,
        @JsonProperty("created_at") LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getNotificationId(),
                n.getType() != null ? n.getType().name() : null,
                n.getTitle(),
                n.getBody(),
                n.getRefType(),
                n.getRefId(),
                n.getIsRead(),
                n.getCreatedAt()
        );
    }
}
