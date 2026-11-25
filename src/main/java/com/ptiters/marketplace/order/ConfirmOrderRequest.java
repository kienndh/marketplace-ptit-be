package com.ptiters.marketplace.order;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ConfirmOrderRequest(
        @JsonProperty("seller_note") String sellerNote
) {}
