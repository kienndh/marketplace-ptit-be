package com.ptiters.marketplace.feedback;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho SiteFeedback. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface SiteFeedbackRepository extends JpaRepository<SiteFeedback, Long> {
}
