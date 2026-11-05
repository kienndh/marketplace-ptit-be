
package com.ptiters.marketplace.product;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateProductRequest {

    @Size(min = 1, max = 255,
            message = "Title must be between 1 and 255 characters")
    private String title;

    private String description;

    @DecimalMin(value = "0",
            message = "Price must not be negative")
    @DecimalMax(value = "50000000",
            message = "Price must not exceed 50 million")
    private BigDecimal price;

    @Min(value = 0,
            message = "Stock must not be negative")
    private Integer stock;
}
