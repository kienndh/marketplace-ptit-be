package com.ptiters.marketplace.auction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Python: {@code PlaceBidRequest} — {@code amount > 0}. */
public record PlaceBidRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount
) {
}
