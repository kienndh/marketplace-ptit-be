package com.ptiters.marketplace.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho MessageReaction. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {
}
