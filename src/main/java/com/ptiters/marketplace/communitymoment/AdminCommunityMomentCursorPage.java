package com.ptiters.marketplace.communitymoment;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AdminCommunityMomentCursorPage(
    List<AdminCommunityMomentItem> items,
    @JsonProperty("next_cursor") String nextCursor,
    @JsonProperty("has_more") boolean hasMore
) {}
