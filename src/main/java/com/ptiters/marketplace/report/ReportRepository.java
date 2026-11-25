package com.ptiters.marketplace.report;

import java.util.List;
import java.util.Optional;

public interface ReportRepository {
    Report save(Report report);
    Optional<Report> findById(Long id);
    List<Report> findAll(int limit, int offset);
    List<Report> findByTarget(ReportTargetType targetType, Long targetId);
    List<Report> findByReporterId(Long reporterId);
    long countByStatus(ReportStatus status);
}
