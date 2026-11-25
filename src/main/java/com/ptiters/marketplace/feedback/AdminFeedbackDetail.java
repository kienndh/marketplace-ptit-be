package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

public record AdminFeedbackDetail(
        @JsonProperty("feedback_id") Long feedbackId,
        AdminFeedbackSender sender,
        String content,
        @JsonProperty("created_at") LocalDateTime createdAt,
        List<AdminFeedbackAttachmentItem> attachments
) {}
