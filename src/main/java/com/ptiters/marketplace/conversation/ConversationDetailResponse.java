package com.ptiters.marketplace.conversation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

public record ConversationDetailResponse(
        @JsonProperty("conversation_id") Long conversationId,
        String type,
        String title,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("created_at") LocalDateTime createdAt
) {}
