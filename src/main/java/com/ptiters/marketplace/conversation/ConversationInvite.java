package com.ptiters.marketplace.conversation;

import lombok.*;
import java.time.LocalDateTime;

/**
 * Domain model representing conversation_invites table in PostgreSQL.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationInvite {
    private Long inviteId;
    private Long conversationId;
    private Long inviterId;
    private Long inviteeId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;
}
