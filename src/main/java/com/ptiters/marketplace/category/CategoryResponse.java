package com.ptiters.marketplace.category;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CategoryResponse {
    @JsonProperty("category_id")
    private Integer categoryId;
    private String name;
    private String slug;
}
