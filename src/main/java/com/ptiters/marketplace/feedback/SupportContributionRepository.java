package com.ptiters.marketplace.feedback;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho SupportContribution. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface SupportContributionRepository extends JpaRepository<SupportContribution, Long> {
}
