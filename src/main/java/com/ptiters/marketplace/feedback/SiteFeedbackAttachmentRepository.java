package com.ptiters.marketplace.feedback;

import java.util.List;
import java.util.Optional;

public interface SiteFeedbackAttachmentRepository {
    SiteFeedbackAttachment save(SiteFeedbackAttachment attachment);
    Optional<SiteFeedbackAttachment> findById(Long id);
    List<SiteFeedbackAttachment> findByFeedbackId(Long feedbackId);
}
