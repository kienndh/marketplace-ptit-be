package com.ptiters.marketplace.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductCreateRequest(
        @NotNull(message = "Danh mục không được để trống")
        @JsonProperty("category_id")
        Integer categoryId,

        @NotBlank(message = "Tiêu đề không được để trống")
        @Size(max = 255, message = "Tiêu đề không được vượt quá 255 ký tự")
        String title,

        String description,

        @NotNull(message = "Giá không được để trống")
        @DecimalMin(value = "0", message = "Giá không được nhỏ hơn 0")
        BigDecimal price,

        @JsonProperty("listing_type")
        ListingType listingType,

        @Min(value = 1, message = "Số lượng phải ít nhất là 1")
        Integer stock
) {}
