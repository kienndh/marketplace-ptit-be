package com.ptiters.marketplace.conversation;

import java.util.List;

public record MessageListResponse(
        List<MessageResponse> items,
        String nextCursor,
        boolean hasMore
) {}
