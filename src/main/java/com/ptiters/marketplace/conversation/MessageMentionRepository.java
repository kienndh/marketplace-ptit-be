package com.ptiters.marketplace.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho MessageMention. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface MessageMentionRepository extends JpaRepository<MessageMention, Long> {
}
