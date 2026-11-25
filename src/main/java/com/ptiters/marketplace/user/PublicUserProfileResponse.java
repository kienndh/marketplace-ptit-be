package com.ptiters.marketplace.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PublicUserProfileResponse {

    @JsonProperty("public_id")
    private UUID publicId;

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    @JsonProperty("bio")
    private String bio;

    @JsonProperty("delivery_area")
    private String deliveryArea;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("social_links")
    private List<SocialLink> socialLinks;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("active_listing_count")
    private int activeListingCount = 0;

    @JsonProperty("completed_sold_count")
    private int completedSoldCount = 0;

    @JsonProperty("rating_average")
    private Double ratingAverage;

    @JsonProperty("review_count")
    private int reviewCount = 0;
}
