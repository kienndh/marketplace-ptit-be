package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminSupportContributionItem(
    Long id,
    @JsonProperty("user_id") Long userId,
    BigDecimal amount,
    @JsonProperty("donor_name") String donorName,
    @JsonProperty("donor_message") String donorMessage,
    @JsonProperty("is_public") Boolean isPublic,
    @JsonProperty("donated_at") LocalDateTime donatedAt
) {}
