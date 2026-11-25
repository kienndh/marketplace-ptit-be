package com.ptiters.marketplace.order;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CancelOrderRequest(
        @JsonProperty("cancel_reason") String cancelReason
) {}
