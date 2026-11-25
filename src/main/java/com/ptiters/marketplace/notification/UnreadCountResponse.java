package com.ptiters.marketplace.notification;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UnreadCountResponse(
    @JsonProperty("unread_count") long unreadCount
) {}
