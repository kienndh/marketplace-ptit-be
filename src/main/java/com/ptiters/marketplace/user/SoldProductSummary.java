package com.ptiters.marketplace.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SoldProductSummary {

    @JsonProperty("product_id")
    private Long productId;

    @JsonProperty("product_title")
    private String productTitle;

    @JsonProperty("product_image_url")
    private String productImageUrl;

    @JsonProperty("total_sold_quantity")
    private int totalSoldQuantity;

    @JsonProperty("last_sold_at")
    private LocalDateTime lastSoldAt;

    @JsonProperty("rating_average")
    private Double ratingAverage;

    @JsonProperty("review_count")
    private int reviewCount = 0;

    @JsonProperty("recent_reviews")
    private List<SoldProductTransactionReview> recentReviews = new ArrayList<>();
}
