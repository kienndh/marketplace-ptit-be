
package com.ptiters.marketplace.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductFilterForm {

    @JsonProperty("category_id")
    private Integer categoryId;

    @DecimalMin(value = "0", message = "Minimum price must not be negative")
    @JsonProperty("min_price")
    private BigDecimal minPrice;

    @DecimalMin(value = "0", message = "Maximum price must not be negative")
    @JsonProperty("max_price")
    private BigDecimal maxPrice;

    @JsonProperty("listing_type")
    private String listingType;

    @Size(max = 100, message = "Keyword must not exceed 100 characters")
    private String keyword;

}
