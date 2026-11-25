package com.ptiters.marketplace.conversation;

import lombok.*;
import java.time.LocalDateTime;

/**
 * Domain model representing message_reactions table in PostgreSQL.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageReaction {
    private Long reactionId;
    private Long messageId;
    private Long userId;
    private ReactionKey reactionKey;
    private LocalDateTime createdAt;
}
