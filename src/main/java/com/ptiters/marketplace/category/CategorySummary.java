package com.ptiters.marketplace.category;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CategorySummary(
        @JsonProperty("category_id") Integer categoryId,
        String name,
        String slug,
        @JsonProperty("icon_url") String iconUrl
) {}
