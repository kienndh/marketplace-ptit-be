package com.ptiters.marketplace.conversation;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ConversationCreateRequest(
        @JsonProperty("product_id") Long productId,
        @JsonProperty("initial_message") String initialMessage
) {}
