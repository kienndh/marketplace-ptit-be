package com.ptiters.marketplace.feedback;

import java.util.List;

public record SupporterLeaderboardResponse(
    List<SupporterLeaderboardItem> items
) {}
