package com.ptiters.marketplace.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModerationAction {
    private Long id;
    private Long reportId;
    private Long adminId;
    private Long targetUserId;
    private ModerationActionType actionType;
    private String reason;
    private Integer durationDays;
    private LocalDateTime createdAt;
}
