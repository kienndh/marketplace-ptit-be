package com.ptiters.marketplace.dailyneed;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record DailyNeedResponse(
    @JsonProperty("daily_need_id") Long dailyNeedId,
    String content,
    @JsonProperty("note_date") LocalDate noteDate,
    @JsonProperty("created_at") OffsetDateTime createdAt,
    @JsonProperty("updated_at") OffsetDateTime updatedAt,
    DailyNeedAuthor author,
    @JsonProperty("checkin_created") boolean checkinCreated
) {}
