package com.ptiters.marketplace.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AdminProductApprovalListResponse(
        List<AdminProductApprovalSummary> items,
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("has_more") boolean hasMore
) {}
