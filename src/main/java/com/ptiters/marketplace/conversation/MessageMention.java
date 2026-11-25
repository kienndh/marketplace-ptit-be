package com.ptiters.marketplace.conversation;

import lombok.*;

/**
 * Domain model representing message_mentions table in PostgreSQL.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageMention {
    private Long mentionId;
    private Long messageId;
    private Long mentionedUserId;
}
