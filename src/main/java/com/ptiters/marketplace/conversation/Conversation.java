package com.ptiters.marketplace.conversation;

import lombok.*;
import java.time.LocalDateTime;

/**
 * Domain model representing conversations table in PostgreSQL.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {
    private Long conversationId;
    private String type;
    private String title;
    private String avatarUrl;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
