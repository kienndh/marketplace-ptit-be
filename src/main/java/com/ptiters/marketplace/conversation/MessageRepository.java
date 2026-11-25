package com.ptiters.marketplace.conversation;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for messages table (PostgreSQL: messages).
 */
public interface MessageRepository {
    Optional<Message> findById(Long messageId);
    List<Message> findByConversationId(Long conversationId);
    Message save(Message message);
}
