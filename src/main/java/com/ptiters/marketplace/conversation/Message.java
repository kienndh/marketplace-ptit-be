package com.ptiters.marketplace.conversation;

import lombok.*;
import java.time.LocalDateTime;

/**
 * Domain model representing messages table in PostgreSQL.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {
    private Long messageId;
    private Long conversationId;
    private Long senderId;
    private String content;
    private String messageType;
    private Long replyToMessageId;
    private Boolean isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
