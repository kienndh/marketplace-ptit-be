package com.ptiters.marketplace.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record NotificationResponse(
    Long id,
    @JsonProperty("user_id") Long userId,
    String type,
    String title,
    String content,
    @JsonProperty("reference_type") String referenceType,
    @JsonProperty("reference_id") Long referenceId,
    @JsonProperty("is_read") Boolean isRead,
    @JsonProperty("read_at") LocalDateTime readAt,
    String metadata,
    @JsonProperty("created_at") LocalDateTime createdAt
) {}
