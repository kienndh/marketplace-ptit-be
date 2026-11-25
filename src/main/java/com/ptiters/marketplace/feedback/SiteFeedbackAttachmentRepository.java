package com.ptiters.marketplace.feedback;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho SiteFeedbackAttachment. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface SiteFeedbackAttachmentRepository extends JpaRepository<SiteFeedbackAttachment, Long> {
}
