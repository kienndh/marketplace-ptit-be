package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AdminFeedbackListPage(
        List<AdminFeedbackListItem> items,
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("has_more") boolean hasMore
) {}
