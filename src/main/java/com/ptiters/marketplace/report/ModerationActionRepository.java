package com.ptiters.marketplace.report;

import java.util.List;
import java.util.Optional;

public interface ModerationActionRepository {
    ModerationAction save(ModerationAction action);
    Optional<ModerationAction> findById(Long id);
    List<ModerationAction> findByReportId(Long reportId);
    List<ModerationAction> findByTargetUserId(Long targetUserId);
}
