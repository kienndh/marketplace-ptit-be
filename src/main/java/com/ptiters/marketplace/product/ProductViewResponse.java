package com.ptiters.marketplace.product;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProductViewResponse(
        @JsonProperty("product_id") Long productId,
        @JsonProperty("view_count") Integer viewCount,
        @JsonProperty("counted") Boolean counted
) {}
