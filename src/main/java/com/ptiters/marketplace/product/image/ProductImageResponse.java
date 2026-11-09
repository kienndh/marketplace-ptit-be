
package com.ptiters.marketplace.product.image;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductImageResponse {

    @JsonProperty("image_id")
    private Long imageId;

    @JsonProperty("product_id")
    private Long productId;

    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("sort_order")
    private Short sortOrder;

    @JsonProperty("is_thumbnail")
    private Boolean thumbnail;
}
