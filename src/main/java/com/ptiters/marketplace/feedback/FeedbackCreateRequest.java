package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record FeedbackCreateRequest(
    @NotBlank String content,
    @JsonProperty("sender_name") String senderName,
    @JsonProperty("is_anonymous") Boolean isAnonymous
) {}
