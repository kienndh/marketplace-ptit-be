package com.ptiters.marketplace.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho MessageAttachment. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, Long> {
}
