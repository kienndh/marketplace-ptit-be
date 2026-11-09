
package com.ptiters.marketplace.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ptiters.marketplace.product.image.ProductImageResponse;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ProductResponse {

    @JsonProperty("product_id")
    private Long productId;

    @JsonProperty("seller_id")
    private Long sellerId;

    @JsonProperty("category_id")
    private Integer categoryId;

    private String title;

    private String description;

    @JsonProperty("listing_type")
    private String listingType;

    private BigDecimal price;

    private Integer stock;

    private String status;

    @JsonProperty("view_count")
    private Integer viewCount;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("images")
    private List<ProductImageResponse> images = new ArrayList<>();

}
