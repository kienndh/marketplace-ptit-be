package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record MyFeedbackItem(
    Long id,
    String content,
    @JsonProperty("is_anonymous") Boolean isAnonymous,
    @JsonProperty("is_public") Boolean isPublic,
    @JsonProperty("admin_notes") String adminNotes,
    @JsonProperty("created_at") LocalDateTime createdAt
) {}
