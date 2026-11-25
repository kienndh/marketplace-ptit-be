package com.ptiters.marketplace.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho ConversationMember. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface ConversationMemberRepository extends JpaRepository<ConversationMember, ConversationMemberId> {
}
