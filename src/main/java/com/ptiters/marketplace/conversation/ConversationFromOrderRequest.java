package com.ptiters.marketplace.conversation;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ConversationFromOrderRequest(
        @JsonProperty("order_id") Long orderId,
        @JsonProperty("initial_message") String initialMessage
) {}
