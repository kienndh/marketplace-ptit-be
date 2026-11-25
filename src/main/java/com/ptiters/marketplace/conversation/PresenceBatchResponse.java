package com.ptiters.marketplace.conversation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record PresenceBatchResponse(
        @JsonProperty("online_status") Map<Long, Boolean> onlineStatus
) {}
