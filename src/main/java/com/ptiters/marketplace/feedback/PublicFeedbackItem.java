package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record PublicFeedbackItem(
        @JsonProperty("feedback_id") Long feedbackId,
        String content,
        @JsonProperty("created_at") LocalDateTime createdAt
) {}
