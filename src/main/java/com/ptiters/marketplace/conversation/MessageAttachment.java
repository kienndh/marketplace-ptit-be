package com.ptiters.marketplace.conversation;

import lombok.*;
import java.time.LocalDateTime;

/**
 * Domain model representing message_attachments table in PostgreSQL.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageAttachment {
    private Long attachmentId;
    private Long messageId;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String mimeType;
    private LocalDateTime createdAt;
}
