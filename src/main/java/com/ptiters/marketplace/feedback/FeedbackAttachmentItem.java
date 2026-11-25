package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FeedbackAttachmentItem(
        @JsonProperty("attachment_id") Long attachmentId,
        String kind,
        @JsonProperty("mime_type") String mimeType,
        @JsonProperty("size_bytes") Long sizeBytes,
        @JsonProperty("duration_ms") Long durationMs
) {
    public static FeedbackAttachmentItem from(SiteFeedbackAttachment a) {
        return new FeedbackAttachmentItem(
                a.getAttachmentId(),
                a.getKind() != null ? a.getKind().name() : null,
                a.getMimeType(),
                a.getSizeBytes(),
                a.getDurationMs()
        );
    }
}
