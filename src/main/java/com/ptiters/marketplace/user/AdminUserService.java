package com.ptiters.marketplace.user;

import com.ptiters.marketplace.exception.ApiException;
import com.ptiters.marketplace.report.ModerationAction;
import com.ptiters.marketplace.user.AdminUserDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** Tương ứng {@code users/admin_service.py}. Việc kiểm tra ADMIN do controller thực hiện. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private final AdminUserQueryRepository repo;

    public ListResponse listUsers(int page, int pageSize, String search, String status, String capability, String role) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw unprocessable("page >= 1 và page_size trong khoảng 1..100.");
        }
        UserStatus statusFilter = parseEnum(status, UserStatus.class, "status");
        UserRole roleFilter = parseEnum(role, UserRole.class, "role");
        String cap = null;
        if (capability != null && !capability.isBlank() && !"ALL".equalsIgnoreCase(capability)) {
            if (!"BUY".equals(capability) && !"SELL".equals(capability)) {
                throw unprocessable("capability_restriction chỉ chấp nhận ALL, BUY, SELL.");
            }
            cap = capability;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        SummaryStats stats = repo.stats(now);
        var result = repo.listUsers(page, pageSize, search, statusFilter, roleFilter, cap, now);
        List<Long> ids = result.users().stream().map(User::getUserId).toList();
        Map<Long, List<String>> restrictions = repo.activeRestrictions(ids, now);
        Map<Long, Long> reportCounts = repo.subjectReportCounts(ids);

        List<ListItem> items = result.users().stream().map(u -> {
            List<String> restr = restrictions.getOrDefault(u.getUserId(), List.of());
            boolean blocked = u.getStatus() == UserStatus.BANNED || u.getStatus() == UserStatus.INACTIVE;
            boolean admin = u.getRole() == UserRole.ADMIN;
            boolean canBuy = !blocked && (admin || !restr.contains("BUY"));
            boolean canSell = !blocked && (admin || !restr.contains("SELL"));
            return new ListItem(u.getUserId(), u.getPublicId(), u.getEmail(), u.getFullName(), u.getAvatarUrl(),
                    u.getRole().name(), u.getStatus().name(), u.getCreatedAt(), u.getLastActiveAt(), canBuy, canSell,
                    restr, reportCounts.getOrDefault(u.getUserId(), 0L));
        }).toList();
        int totalPages = result.total() > 0 ? (int) Math.ceil(result.total() / (double) pageSize) : 1;
        return new ListResponse(items, result.total(), page, pageSize, totalPages, stats);
    }

    public DetailResponse getDetail(Long userId) {
        User u = repo.usersByIds(List.of(userId)).get(userId);
        if (u == null) {
            throw ApiException.notFound("Người dùng không tồn tại.");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<UserCapabilityRestriction> all = repo.allRestrictions(userId);
        Set<Long> names = new HashSet<>();
        all.forEach(r -> {
            names.add(r.getImposedBy());
            if (r.getRevokedBy() != null) {
                names.add(r.getRevokedBy());
            }
        });
        Map<Long, User> users = repo.usersByIds(names);
        List<RestrictionDetail> details = all.stream().map(r -> new RestrictionDetail(
                r.getRestrictionId(), r.getCapability(), r.getStartsAt(), r.getExpiresAt(), r.getRevokedAt(),
                r.getRevokedAt() == null && !r.getStartsAt().isAfter(now) && (r.getExpiresAt() == null || r.getExpiresAt().isAfter(now)),
                r.getSourceReportId(), r.getImposedBy(), name(users.get(r.getImposedBy())),
                r.getRevokedBy(), r.getRevokedBy() == null ? null : name(users.get(r.getRevokedBy())), r.getCreatedAt())).toList();
        List<RestrictionDetail> active = details.stream().filter(RestrictionDetail::isActive).toList();

        boolean blocked = u.getStatus() == UserStatus.BANNED || u.getStatus() == UserStatus.INACTIVE;
        boolean admin = u.getRole() == UserRole.ADMIN;
        boolean canBuy = !blocked && (admin || active.stream().noneMatch(r -> "BUY".equals(r.capability())));
        boolean canSell = !blocked && (admin || active.stream().noneMatch(r -> "SELL".equals(r.capability())));

        List<EligibleReport> eligible = repo.eligibleReports(userId).stream().map(r -> new EligibleReport(
                r.getReportId(), r.getTargetType().name(), r.getTargetId(), r.getReason().name(), r.getDescription(),
                r.getStatus().name(), r.getCreatedAt())).toList();

        return new DetailResponse(u.getUserId(), u.getPublicId(), u.getEmail(), u.getFullName(), u.getAvatarUrl(),
                u.getPhone(), u.getBio(), u.getDeliveryArea(), u.getRole().name(), u.getStatus().name(), u.getCreatedAt(),
                u.getLastActiveAt(), canBuy, canSell, repo.activeListingCount(userId),
                repo.subjectReportCounts(List.of(userId)).getOrDefault(userId, 0L),
                repo.countSupportedSubjectReports(userId), repo.countSentReports(userId), active, details, eligible);
    }

    public List<ModerationHistoryItem> moderationHistory(Long userId) {
        if (repo.usersByIds(List.of(userId)).get(userId) == null) {
            throw ApiException.notFound("Người dùng không tồn tại.");
        }
        List<ModerationAction> actions = repo.moderationHistory(userId);
        Map<Long, User> admins = repo.usersByIds(actions.stream().map(ModerationAction::getAdminId).toList());
        return actions.stream().map(a -> {
            User ad = admins.get(a.getAdminId());
            return new ModerationHistoryItem(a.getModerationActionId(), a.getActionType().name(), a.getPreviousStatus(),
                    a.getNewStatus(), a.getCreatedAt(), a.getAdminId(), name(ad), ad == null ? null : ad.getEmail(), a.getReportId());
        }).toList();
    }

    private static String name(User u) {
        return u == null ? null : u.getFullName();
    }

    private static <E extends Enum<E>> E parseEnum(String v, Class<E> type, String field) {
        if (v == null || v.isBlank() || "ALL".equalsIgnoreCase(v)) {
            return null;
        }
        try {
            return Enum.valueOf(type, v);
        } catch (IllegalArgumentException e) {
            throw unprocessable("Giá trị '" + v + "' không hợp lệ cho " + field + ".");
        }
    }

    private static ApiException unprocessable(String msg) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, msg);
    }
}
