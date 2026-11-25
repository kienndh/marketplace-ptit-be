package com.ptiters.marketplace.conversation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record DirectConversationCreateRequest(
        @JsonProperty("recipient_public_id") UUID recipientPublicId,
        @JsonProperty("initial_message") String initialMessage
) {}
