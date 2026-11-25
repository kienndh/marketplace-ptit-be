package com.ptiters.marketplace.product;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ImageReorderItem(
        @JsonProperty("image_id") Long imageId,
        @JsonProperty("sort_order") Short sortOrder
) {}
