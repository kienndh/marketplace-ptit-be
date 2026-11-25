package com.ptiters.marketplace.dailyneed;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record DailyNeedPublicResponse(
    @JsonProperty("daily_need_id") Long dailyNeedId,
    String content,
    @JsonProperty("note_date") LocalDate noteDate,
    @JsonProperty("updated_at") OffsetDateTime updatedAt,
    DailyNeedAuthor author
) {}
