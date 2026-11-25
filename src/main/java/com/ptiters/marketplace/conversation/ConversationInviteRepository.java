package com.ptiters.marketplace.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho ConversationInvite. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface ConversationInviteRepository extends JpaRepository<ConversationInvite, Long> {
}
