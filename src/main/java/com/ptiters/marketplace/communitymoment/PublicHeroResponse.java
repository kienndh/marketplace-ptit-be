package com.ptiters.marketplace.communitymoment;

import java.util.List;

public record PublicHeroResponse(
    List<PublicCommunityMomentItem> items
) {}
