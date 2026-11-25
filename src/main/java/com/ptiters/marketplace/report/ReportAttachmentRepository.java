package com.ptiters.marketplace.report;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho ReportAttachment. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface ReportAttachmentRepository extends JpaRepository<ReportAttachment, Long> {
}
