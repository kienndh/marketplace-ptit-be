package com.ptiters.marketplace.report;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho ModerationAction. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface ModerationActionRepository extends JpaRepository<ModerationAction, Long> {
}
