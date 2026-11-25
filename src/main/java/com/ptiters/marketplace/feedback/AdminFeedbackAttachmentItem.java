package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdminFeedbackAttachmentItem(
        @JsonProperty("attachment_id") Long attachmentId,
        String kind,
        @JsonProperty("mime_type") String mimeType,
        @JsonProperty("size_bytes") Long sizeBytes,
        @JsonProperty("duration_ms") Long durationMs,
        @JsonProperty("stream_url") String streamUrl
) {}
