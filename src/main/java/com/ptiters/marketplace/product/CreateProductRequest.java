
package com.ptiters.marketplace.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateProductRequest {

    @NotNull(message = "Category is required")
    @JsonProperty("category_id")
    private Integer categoryId;

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0", message = "Price must not be negative")
    @DecimalMax(value = "50000000", message = "Price must not exceed 50 million")
    private BigDecimal price;

    @NotNull(message = "Stock is required")
    @Min(value = 1, message = "Stock must be at least 1")
    private Integer stock;
}
