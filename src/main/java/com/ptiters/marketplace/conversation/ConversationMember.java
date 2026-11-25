package com.ptiters.marketplace.conversation;

import lombok.*;
import java.time.LocalDateTime;

/**
 * Domain model representing conversation_members table in PostgreSQL.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationMember {
    private Long memberId;
    private Long conversationId;
    private Long userId;
    private String role;
    private String nickname;
    private LocalDateTime joinedAt;
    private LocalDateTime lastReadAt;
}
