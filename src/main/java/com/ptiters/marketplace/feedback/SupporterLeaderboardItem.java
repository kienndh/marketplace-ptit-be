package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record SupporterLeaderboardItem(
    int rank,
    @JsonProperty("donor_name") String donorName,
    BigDecimal amount,
    @JsonProperty("donor_message") String donorMessage
) {}
