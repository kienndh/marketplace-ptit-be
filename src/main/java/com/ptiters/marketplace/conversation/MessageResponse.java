package com.ptiters.marketplace.conversation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

public record MessageResponse(
        @JsonProperty("message_id") Long messageId,
        @JsonProperty("conversation_id") Long conversationId,
        @JsonProperty("sender_id") Long senderId,
        @JsonProperty("sender_name") String senderName,
        @JsonProperty("sender_avatar") String senderAvatar,
        String content,
        @JsonProperty("message_type") String messageType,
        @JsonProperty("created_at") LocalDateTime createdAt
) {}
