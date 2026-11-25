package com.ptiters.marketplace.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho Message. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface MessageRepository extends JpaRepository<Message, Long> {
}
