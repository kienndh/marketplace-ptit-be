package com.ptiters.marketplace.feedback;

import java.util.List;
import java.util.Optional;

public interface SupportContributionRepository {
    SupportContribution save(SupportContribution contribution);
    Optional<SupportContribution> findById(Long id);
    List<SupportContribution> findPublicContributions(int limit, int offset);
    List<SupportContribution> findAll(int limit, int offset);
    void deleteById(Long id);
}
