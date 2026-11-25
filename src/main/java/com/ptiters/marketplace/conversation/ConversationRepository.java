package com.ptiters.marketplace.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho Conversation. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
}
