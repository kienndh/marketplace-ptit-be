package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

public record AdminFeedbackDetail(
    Long id,
    @JsonProperty("user_id") Long userId,
    @JsonProperty("sender_name") String senderName,
    String content,
    @JsonProperty("is_anonymous") Boolean isAnonymous,
    @JsonProperty("is_public") Boolean isPublic,
    @JsonProperty("admin_notes") String adminNotes,
    @JsonProperty("created_at") LocalDateTime createdAt,
    List<String> attachments
) {}
