package com.ptiters.marketplace.conversation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record ConversationSummaryResponse(
        @JsonProperty("conversation_id") Long conversationId,
        String type,
        String title,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("last_message") String lastMessage,
        @JsonProperty("last_message_at") LocalDateTime lastMessageAt,
        @JsonProperty("unread_count") Integer unreadCount
) {}
