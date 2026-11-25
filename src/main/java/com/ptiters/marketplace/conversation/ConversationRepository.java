package com.ptiters.marketplace.conversation;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for conversations table (PostgreSQL: conversations).
 */
public interface ConversationRepository {
    Optional<Conversation> findById(Long conversationId);
    List<Conversation> findByUserId(Long userId);
    Conversation save(Conversation conversation);
}
