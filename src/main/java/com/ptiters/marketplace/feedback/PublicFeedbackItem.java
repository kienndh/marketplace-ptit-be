package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record PublicFeedbackItem(
    Long id,
    @JsonProperty("sender_name") String senderName,
    String content,
    @JsonProperty("created_at") LocalDateTime createdAt
) {}
