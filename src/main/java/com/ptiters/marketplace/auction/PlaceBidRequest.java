package com.ptiters.marketplace.auction;

import java.math.BigDecimal;

public record PlaceBidRequest(
        BigDecimal amount
) {}
