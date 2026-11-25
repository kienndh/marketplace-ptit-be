package com.ptiters.marketplace.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {
    private Long id;
    private Long userId;
    private NotificationType type;
    private String title;
    private String content;
    private String referenceType;
    private Long referenceId;
    private Boolean isRead;
    private LocalDateTime readAt;
    private String metadata;
    private LocalDateTime createdAt;
}
