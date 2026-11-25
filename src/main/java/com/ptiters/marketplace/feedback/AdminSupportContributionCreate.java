package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record AdminSupportContributionCreate(
    @JsonProperty("user_id") Long userId,
    @NotNull BigDecimal amount,
    @NotBlank @JsonProperty("donor_name") String donorName,
    @JsonProperty("donor_message") String donorMessage,
    @JsonProperty("is_public") Boolean isPublic
) {}
