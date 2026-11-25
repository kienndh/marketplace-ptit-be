package com.ptiters.marketplace.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record NotificationListResponse(
        List<NotificationResponse> items,
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("has_more") boolean hasMore
) {}
