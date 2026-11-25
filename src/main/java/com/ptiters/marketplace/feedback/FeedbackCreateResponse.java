package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record FeedbackCreateResponse(
    Long id,
    String content,
    @JsonProperty("is_anonymous") Boolean isAnonymous,
    @JsonProperty("created_at") LocalDateTime createdAt
) {}
