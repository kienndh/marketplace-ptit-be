package com.ptiters.marketplace.dailyneed;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho DailyNeed. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface DailyNeedRepository extends JpaRepository<DailyNeed, Long> {
}
