package com.ptiters.marketplace.dailyneed;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho DailyJournalCheckin. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface DailyJournalCheckinRepository extends JpaRepository<DailyJournalCheckin, Long> {
}
