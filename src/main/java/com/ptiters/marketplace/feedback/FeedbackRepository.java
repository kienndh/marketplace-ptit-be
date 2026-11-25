package com.ptiters.marketplace.feedback;

import java.util.List;
import java.util.Optional;

public interface FeedbackRepository {
    SiteFeedback save(SiteFeedback feedback);
    Optional<SiteFeedback> findById(Long id);
    List<SiteFeedback> findPublicFeedbacks(int limit, int offset);
    List<SiteFeedback> findByUserId(Long userId, int limit, int offset);
    List<SiteFeedback> findAll(int limit, int offset);
    void deleteById(Long id);
}
