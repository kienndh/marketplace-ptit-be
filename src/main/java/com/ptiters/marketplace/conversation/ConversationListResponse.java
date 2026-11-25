package com.ptiters.marketplace.conversation;

import java.util.List;

public record ConversationListResponse(
        List<ConversationSummaryResponse> items
) {}
