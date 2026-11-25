package com.ptiters.marketplace.worker;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho MediaCleanupJob. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface MediaCleanupJobRepository extends JpaRepository<MediaCleanupJob, Long> {
}
