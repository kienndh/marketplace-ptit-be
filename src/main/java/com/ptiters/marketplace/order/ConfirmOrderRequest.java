package com.ptiters.marketplace.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

/** Python: {@code ConfirmOrderRequest} (body có thể là {@code {}}). */
public record ConfirmOrderRequest(
        @Size(max = 255) @JsonProperty("payment_note") String paymentNote
) {
}
