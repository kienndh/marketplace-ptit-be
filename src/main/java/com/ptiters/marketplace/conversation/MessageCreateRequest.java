package com.ptiters.marketplace.conversation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record MessageCreateRequest(
        String content,
        @JsonProperty("message_type") String messageType,
        @JsonProperty("reply_to_message_id") Long replyToMessageId,
        @JsonProperty("mentioned_user_ids") List<Long> mentionedUserIds
) {}
