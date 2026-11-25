package com.ptiters.marketplace.dailyneed;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyJournalCheckinRepository {
    DailyJournalCheckin save(DailyJournalCheckin checkin);
    Optional<DailyJournalCheckin> findByUserIdAndCheckinDate(Long userId, LocalDate checkinDate);
    long countStreakByUserId(Long userId);
}
