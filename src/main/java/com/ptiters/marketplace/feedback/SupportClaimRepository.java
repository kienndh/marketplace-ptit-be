package com.ptiters.marketplace.feedback;

import java.util.List;
import java.util.Optional;

public interface SupportClaimRepository {
    SupportClaim save(SupportClaim claim);
    Optional<SupportClaim> findById(Long id);
    List<SupportClaim> findByUserId(Long userId);
    List<SupportClaim> findByStatus(SupportClaimStatus status);
}
