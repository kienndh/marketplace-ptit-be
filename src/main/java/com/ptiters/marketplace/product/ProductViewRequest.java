package com.ptiters.marketplace.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record ProductViewRequest(
        @JsonProperty("anonymous_viewer_id") UUID anonymousViewerId
) {}
