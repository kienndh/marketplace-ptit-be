package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record AdminFeedbackListItem(
        @JsonProperty("feedback_id") Long feedbackId,
        AdminFeedbackSender sender,
        String content,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("image_count") int imageCount,
        @JsonProperty("video_count") int videoCount
) {}
