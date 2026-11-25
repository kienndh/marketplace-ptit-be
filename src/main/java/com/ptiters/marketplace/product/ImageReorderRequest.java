package com.ptiters.marketplace.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ImageReorderRequest(
        @JsonProperty("images") List<ImageReorderItem> images
) {}
