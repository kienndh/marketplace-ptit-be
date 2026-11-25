package com.ptiters.marketplace.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.UUID;

public record SellerDetailSummary(
        @JsonProperty("public_id") UUID publicId,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("delivery_area") String deliveryArea,
        @JsonProperty("default_meetup_area") String defaultMeetupArea,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("rating_average") Float ratingAverage,
        @JsonProperty("review_count") int reviewCount
) {}
