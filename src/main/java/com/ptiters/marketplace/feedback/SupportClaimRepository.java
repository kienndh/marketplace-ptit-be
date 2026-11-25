package com.ptiters.marketplace.feedback;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho SupportClaim. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface SupportClaimRepository extends JpaRepository<SupportClaim, Long> {
}
