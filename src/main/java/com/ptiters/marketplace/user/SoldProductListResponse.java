package com.ptiters.marketplace.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SoldProductListResponse {

    @JsonProperty("items")
    private List<SoldProductSummary> items = new ArrayList<>();

    @JsonProperty("total_count")
    private int totalCount = 0;
}
