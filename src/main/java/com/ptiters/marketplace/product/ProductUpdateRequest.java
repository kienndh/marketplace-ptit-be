package com.ptiters.marketplace.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductUpdateRequest(
        @JsonProperty("category_id")
        Integer categoryId,

        @Size(max = 255, message = "Tiêu đề không được vượt quá 255 ký tự")
        String title,

        String description,

        @DecimalMin(value = "0", message = "Giá không được nhỏ hơn 0")
        BigDecimal price,

        ProductStatus status,

        @Min(value = 0, message = "Số lượng không được âm")
        Integer stock
) {}
