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
public class Report {
    private Long id;
    private Long reporterId;
    private ReportTargetType targetType;
    private Long targetId;
    private ReportReason reason;
    private String description;
    private ReportStatus status;
    private ReportWorkflowState workflowState;
    private Long handledBy;
    private String resolutionNotes;
    private ReportConclusion conclusion;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
