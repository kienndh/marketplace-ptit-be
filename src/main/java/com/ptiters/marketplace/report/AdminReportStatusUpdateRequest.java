package com.ptiters.marketplace.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record AdminReportStatusUpdateRequest(
    @NotNull ReportStatus status,
    @JsonProperty("workflow_state") ReportWorkflowState workflowState,
    @JsonProperty("resolution_notes") String resolutionNotes,
    ReportConclusion conclusion
) {}
