package com.ptiters.marketplace.dailyneed;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyNeedRepository {
    DailyNeed save(DailyNeed dailyNeed);
    Optional<DailyNeed> findById(Long id);
    Optional<DailyNeed> findByUserIdAndNoteDate(Long userId, LocalDate noteDate);
    List<DailyNeed> findRecent(int limit);
    List<DailyNeed> findByDate(LocalDate date, int limit);
    void deleteByUserIdAndNoteDate(Long userId, LocalDate noteDate);
}
