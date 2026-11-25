package com.ptiters.marketplace.report;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho Report. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface ReportRepository extends JpaRepository<Report, Long> {
}
