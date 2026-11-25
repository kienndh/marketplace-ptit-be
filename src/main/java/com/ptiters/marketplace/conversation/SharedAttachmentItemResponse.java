package com.ptiters.marketplace.conversation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record SharedAttachmentItemResponse(
        @JsonProperty("attachment_id") Long attachmentId,
        @JsonProperty("file_url") String fileUrl,
        @JsonProperty("file_name") String fileName,
        @JsonProperty("file_size") Long fileSize,
        @JsonProperty("mime_type") String mimeType,
        @JsonProperty("created_at") LocalDateTime createdAt
) {}
