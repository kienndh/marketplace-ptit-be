package com.ptiters.marketplace.communitymoment;

import java.util.List;
import java.util.Optional;

public interface CommunityMomentRepository {
    CommunityMoment save(CommunityMoment moment);
    Optional<CommunityMoment> findById(Long id);
    List<CommunityMoment> findApprovedMoments(int limit);
    List<CommunityMoment> findAll(int limit, String cursor);
    void deleteById(Long id);
}
