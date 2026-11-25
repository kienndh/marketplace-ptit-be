package com.ptiters.marketplace.favorite;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record FavoriteIdsResponse(
        @JsonProperty("product_ids") List<Long> productIds
) {}
