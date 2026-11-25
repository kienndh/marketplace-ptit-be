package com.ptiters.marketplace.report;

import com.ptiters.marketplace.user.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** {@code GET /api/v1/admin/users/{user_id}/report-history} (router {@code admin_users_router} của reports). */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserReportHistoryController {

    private final AdminReportService adminReportService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/{userId}/report-history")
    public ResponseEntity<SubjectReportHistoryResponse> getUserReportHistory(
            @PathVariable("userId") Long userId,
            @RequestParam(name = "limit", defaultValue = "5") int limit,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "exclude_report_id", required = false) Long excludeReportId,
            @RequestParam(name = "type", defaultValue = "RECEIVED") String type) {
        currentUserResolver.requireAdmin();
        return ResponseEntity.ok(adminReportService.subjectHistory(userId, limit, cursor, excludeReportId, type));
    }
}
