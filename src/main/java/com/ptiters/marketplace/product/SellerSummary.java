package com.ptiters.marketplace.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record SellerSummary(
        @JsonProperty("public_id") UUID publicId,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("avatar_url") String avatarUrl
) {}
