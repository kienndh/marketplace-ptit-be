package com.ptiters.marketplace.dailyneed;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DailyNeedResponse(
    @JsonProperty("daily_need_id") Long dailyNeedId,
    String content,
    @JsonProperty("note_date") LocalDate noteDate,
    @JsonProperty("created_at") LocalDateTime createdAt,
    @JsonProperty("updated_at") LocalDateTime updatedAt,
    DailyNeedAuthor author,
    @JsonProperty("checkin_created") boolean checkinCreated
) {}
