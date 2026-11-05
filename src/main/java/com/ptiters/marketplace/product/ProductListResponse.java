
package com.ptiters.marketplace.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ProductListResponse {

    private List<ProductResponse> items;

    @JsonProperty("next_cursor")
    private String nextCursor;

    @JsonProperty("has_more")
    private boolean hasMore;
}
