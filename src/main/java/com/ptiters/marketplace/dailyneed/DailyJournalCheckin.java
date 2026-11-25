package com.ptiters.marketplace.dailyneed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyJournalCheckin {
    private Long id;
    private Long userId;
    private LocalDate checkinDate;
    private LocalDateTime createdAt;
}
