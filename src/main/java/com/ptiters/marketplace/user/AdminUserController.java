package com.ptiters.marketplace.user;

import com.ptiters.marketplace.user.AdminUserDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public ResponseEntity<ListResponse> list(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "capability_restriction", required = false) String capabilityRestriction,
            @RequestParam(name = "role", required = false) String role) {
        currentUserResolver.requireAdmin();
        return ResponseEntity.ok(adminUserService.listUsers(page, pageSize, search, status, capabilityRestriction, role));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<DetailResponse> detail(@PathVariable("userId") Long userId) {
        currentUserResolver.requireAdmin();
        return ResponseEntity.ok(adminUserService.getDetail(userId));
    }

    @GetMapping("/{userId}/moderation-history")
    public ResponseEntity<List<ModerationHistoryItem>> moderationHistory(@PathVariable("userId") Long userId) {
        currentUserResolver.requireAdmin();
        return ResponseEntity.ok(adminUserService.moderationHistory(userId));
    }
}
