package com.ptiters.marketplace.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record NotificationListResponse(
    List<NotificationResponse> items,
    @JsonProperty("unread_count") long unreadCount,
    @JsonProperty("has_more") boolean hasMore
) {}
