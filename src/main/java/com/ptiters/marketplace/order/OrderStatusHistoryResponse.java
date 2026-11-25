package com.ptiters.marketplace.order;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record OrderStatusHistoryResponse(
        @JsonProperty("history_id") Long historyId,
        @JsonProperty("from_status") String fromStatus,
        @JsonProperty("to_status") String toStatus,
        @JsonProperty("changed_by") Long changedBy,
        @JsonProperty("note") String note,
        @JsonProperty("created_at") LocalDateTime createdAt
) {
}
