package com.ptiters.marketplace.dailyneed;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DailyNeedSummaryResponse(
    @JsonProperty("today_journal") DailyNeedResponse todayJournal,
    @JsonProperty("has_journal_today") boolean hasJournalToday,
    @JsonProperty("has_checked_in_today") boolean hasCheckedInToday,
    @JsonProperty("has_posted_today") boolean hasPostedToday,
    @JsonProperty("streak_count") int streakCount
) {}
