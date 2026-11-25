package com.ptiters.marketplace.report;

import com.ptiters.marketplace.auction.Auction;
import com.ptiters.marketplace.auction.AuctionStatus;
import com.ptiters.marketplace.exception.ApiException;
import com.ptiters.marketplace.integration.MediaStorage;
import com.ptiters.marketplace.notification.NotificationType;
import com.ptiters.marketplace.notification.NotificationWriter;
import com.ptiters.marketplace.order.Order;
import com.ptiters.marketplace.order.OrderRepository;
import com.ptiters.marketplace.product.ListingType;
import com.ptiters.marketplace.product.Product;
import com.ptiters.marketplace.product.ProductModerationStatus;
import com.ptiters.marketplace.realtime.AuctionEventPublisher;
import com.ptiters.marketplace.realtime.UserEventPublisher;
import com.ptiters.marketplace.report.ReportSummaries.*;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserCapabilityRestriction;
import com.ptiters.marketplace.user.UserRepository;
import com.ptiters.marketplace.user.UserRole;
import com.ptiters.marketplace.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Quản trị báo cáo vi phạm: hàng đợi, chi tiết, workflow, chế tài (USER_BAN/UNBAN, PRODUCT_HIDE/RESTORE,
 * CAPABILITY_RESTRICT/RESTORE → BUY/SELL_RESTRICT/RESTORE) và lịch sử theo đối tượng.
 * Tương ứng phần admin của {@code reports/service.py}. Thứ tự khoá cố định: Report → User → Product → Auction.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportService {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String REPORT_NOT_FOUND = "Báo cáo không tồn tại.";
    private static final String ADMIN_PROTECTED = "Không thể áp dụng chế tài lên tài khoản quản trị viên.";

    private final ReportQueryRepository repo;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final NotificationWriter notificationWriter;
    private final UserEventPublisher userEventPublisher;
    private final AuctionEventPublisher auctionEventPublisher;

    // =================================================================== hàng đợi / chi tiết

    public AdminReportListResponse listReports(int limit, String cursor, AdminReportQueueState queueState,
                                               ReportStatus status, ReportWorkflowState workflow,
                                               ReportConclusion conclusion, ReportTargetType targetType) {
        if (limit < 1 || limit > 50) {
            throw unprocessable("limit phải nằm trong khoảng 1..50.");
        }
        if (queueState != null && (status != null || workflow != null || conclusion != null)) {
            throw unprocessable("Không được kết hợp tham số lọc 'queue_state' với 'status', 'workflow', hoặc 'conclusion'.");
        }
        if (status != null && (workflow != null || conclusion != null)) {
            throw unprocessable("Không được kết hợp tham số lọc 'status' truyền thống với 'workflow' hoặc 'conclusion'.");
        }

        List<ReportStatus> statuses = null;
        if (queueState != null) {
            statuses = queueState == AdminReportQueueState.NEEDS_ACTION
                    ? List.of(ReportStatus.OPEN, ReportStatus.REVIEWING)
                    : List.of(ReportStatus.RESOLVED, ReportStatus.REJECTED);
        } else if (status != null) {
            statuses = List.of(status);
        } else if (conclusion != null) {
            if (workflow == ReportWorkflowState.OPEN || workflow == ReportWorkflowState.REVIEWING) {
                throw unprocessable("Không thể lọc kết luận ('conclusion') khi luồng xử lý ('workflow') chưa hoàn tất.");
            }
            statuses = List.of(conclusion == ReportConclusion.SUPPORTED ? ReportStatus.RESOLVED : ReportStatus.REJECTED);
        } else if (workflow != null) {
            statuses = switch (workflow) {
                case OPEN -> List.of(ReportStatus.OPEN);
                case REVIEWING -> List.of(ReportStatus.REVIEWING);
                case COMPLETED -> List.of(ReportStatus.RESOLVED, ReportStatus.REJECTED);
            };
        }

        LocalDateTime cursorAt = null;
        Long cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            Object[] c = ReportCursor.decode(cursor);
            cursorAt = (LocalDateTime) c[0];
            cursorId = (Long) c[1];
        }

        List<Report> fetched = repo.listAdminReports(limit + 1, cursorAt, cursorId, statuses, targetType);
        boolean hasMore = fetched.size() > limit;
        List<Report> page = hasMore ? fetched.subList(0, limit) : fetched;

        Map<String, TargetInfo> targets = resolveTargets(page);
        Map<Long, Long> evidenceCounts = repo.countAttachments(page.stream().map(Report::getReportId).toList());
        List<AdminReportResponse> items = page.stream()
                .map(r -> toResponse(r, targets.get(targetKey(r)), evidenceCounts.getOrDefault(r.getReportId(), 0L)))
                .toList();
        String nextCursor = hasMore && !page.isEmpty()
                ? ReportCursor.encode(page.get(page.size() - 1).getCreatedAt(), page.get(page.size() - 1).getReportId())
                : null;
        return new AdminReportListResponse(items, nextCursor, hasMore);
    }

    public AdminReportDetailResponse getDetail(User admin, Long reportId) {
        Report report = repo.findReport(reportId).orElseThrow(() -> ApiException.notFound(REPORT_NOT_FOUND));
        return buildDetail(admin, report);
    }

    private AdminReportDetailResponse buildDetail(User admin, Report report) {
        TargetInfo target = resolveTargets(List.of(report)).get(targetKey(report));
        List<ReportAttachment> attachments = repo.listAttachments(report.getReportId());
        List<ModerationAction> actions = repo.listActions(report.getReportId());
        Map<Long, User> admins = usersById(actions.stream().map(ModerationAction::getAdminId).toList());

        List<ModerationActionSummary> actionSummaries = actions.stream()
                .map(a -> new ModerationActionSummary(a.getModerationActionId(), a.getActionType().name(),
                        a.getPreviousStatus(), a.getNewStatus(),
                        admins.containsKey(a.getAdminId())
                                ? new AdminHandlerSummary(a.getAdminId(), admins.get(a.getAdminId()).getFullName()) : null,
                        a.getCreatedAt()))
                .toList();

        LocalDateTime now = nowUtc();
        ReportedSubjectSummary subject = null;
        if (report.getTargetType() == ReportTargetType.USER || report.getTargetType() == ReportTargetType.ORDER) {
            Subject s = resolveSubject(report);
            if (s.user() != null) {
                List<UserCapabilityRestriction> active = repo.activeRestrictions(s.user().getUserId(), now);
                Set<String> caps = new HashSet<>();
                active.forEach(r -> caps.add(r.getCapability()));
                boolean activeUser = s.user().getStatus() == UserStatus.ACTIVE;
                subject = new ReportedSubjectSummary(s.user().getUserId(), s.user().getFullName(), s.user().getAvatarUrl(),
                        s.roleInOrder(), s.user().getStatus().name(), activeUser && !caps.contains("BUY"),
                        activeUser && !caps.contains("SELL"),
                        active.stream().map(r -> new AdminCapabilityRestrictionResponse(r.getCapability(), r.getExpiresAt(), false)).toList(),
                        repo.countSubjectReports(s.user().getUserId(), report.getReportId()));
            }
        }

        AdminReportResponse base = toResponse(report, target, attachments.size());
        return AdminReportDetailResponse.of(base,
                attachments.stream().map(AdminReportService::toEvidence).toList(),
                actionSummaries,
                moderationOptions(report.getStatus()),
                availableEnforcementActions(report, admin),
                subject);
    }

    // =================================================================== workflow

    @Transactional
    public AdminReportDetailResponse updateStatus(User admin, Long reportId, ReportStatus newStatus) {
        Report report = repo.lockReport(reportId).orElseThrow(() -> ApiException.notFound(REPORT_NOT_FOUND));
        ReportStatus current = report.getStatus();
        if (current == ReportStatus.RESOLVED || current == ReportStatus.REJECTED) {
            throw ApiException.conflict("Không thể thay đổi trạng thái của báo cáo đã ở trạng thái kết thúc '" + current.name() + "'.");
        }
        Set<ReportStatus> allowed = current == ReportStatus.OPEN
                ? EnumSet.of(ReportStatus.REVIEWING, ReportStatus.RESOLVED, ReportStatus.REJECTED)
                : EnumSet.of(ReportStatus.RESOLVED, ReportStatus.REJECTED);
        if (!allowed.contains(newStatus)) {
            throw ApiException.conflict("Chuyển trạng thái từ '" + current.name() + "' sang '" + newStatus.name() + "' không hợp lệ.");
        }

        report.setStatus(newStatus);
        report.setHandledBy(admin.getUserId());
        if (newStatus == ReportStatus.REVIEWING) {
            report.setHandledAt(null);
        } else {
            report.setHandledAt(nowUtc());
            notifyReporterTerminal(report, newStatus == ReportStatus.RESOLVED);
        }
        repo.flush();
        return buildDetail(admin, report);
    }

    // =================================================================== chế tài

    @Transactional
    public AdminReportDetailResponse enforce(User admin, Long reportId, AdminEnforcementRequest req) {
        return switch (req.action()) {
            case "USER_BAN", "USER_UNBAN" -> enforceUser(admin, reportId, req.action());
            case "PRODUCT_HIDE", "PRODUCT_RESTORE" -> enforceProduct(admin, reportId, req.action());
            case "CAPABILITY_RESTRICT", "CAPABILITY_RESTORE" -> enforceCapability(admin, reportId, req);
            default -> throw unprocessable("Hành động chế tài '" + req.action() + "' không được hỗ trợ.");
        };
    }

    private AdminReportDetailResponse enforceUser(User admin, Long reportId, String action) {
        Report report = repo.lockReport(reportId).orElseThrow(() -> ApiException.notFound(REPORT_NOT_FOUND));
        Long targetUserId = resolveEnforcementTargetUserId(report, "Chỉ có thể thực hiện chế tài người dùng trên báo cáo loại Người dùng hoặc Đơn hàng.");
        User target = repo.lockUser(targetUserId).orElseThrow(() -> ApiException.notFound("Người dùng bị báo cáo không tồn tại."));
        if (target.getRole() == UserRole.ADMIN || target.getUserId().equals(admin.getUserId())) {
            throw ApiException.forbidden(ADMIN_PROTECTED);
        }
        LocalDateTime now = nowUtc();

        if ("USER_BAN".equals(action)) {
            if (report.getStatus() != ReportStatus.OPEN && report.getStatus() != ReportStatus.REVIEWING) {
                throw ApiException.conflict("Không thể khóa tài khoản từ báo cáo đã ở trạng thái kết thúc '" + report.getStatus().name() + "'.");
            }
            if (target.getStatus() == UserStatus.BANNED) {
                throw ApiException.conflict("Tài khoản người dùng đã bị khóa trước đó.");
            }
            if (target.getStatus() == UserStatus.INACTIVE) {
                throw ApiException.conflict("Không thể áp dụng chế tài lên tài khoản đang ngừng hoạt động.");
            }
            String previous = target.getStatus().name();
            target.setStatus(UserStatus.BANNED);
            addAction(report, admin, ReportTargetType.USER, target.getUserId(), ModerationActionType.USER_BAN,
                    previous, UserStatus.BANNED.name(), now);
            report.setStatus(ReportStatus.RESOLVED);
            report.setHandledBy(admin.getUserId());
            report.setHandledAt(now);
            notifyReporterTerminal(report, true);
            repo.flush();
            Long bannedId = target.getUserId();
            AfterCommit.run("revoke sessions of banned user " + bannedId, () -> userEventPublisher.revokeUserSessions(bannedId));
        } else {
            if (target.getStatus() != UserStatus.BANNED) {
                throw ApiException.conflict("Tài khoản người dùng không ở trạng thái bị khóa.");
            }
            Optional<ModerationAction> latest = repo.latestActionForTarget(ReportTargetType.USER, target.getUserId(),
                    List.of(ModerationActionType.USER_BAN, ModerationActionType.USER_UNBAN));
            if (latest.isEmpty() || latest.get().getActionType() != ModerationActionType.USER_BAN
                    || !latest.get().getReportId().equals(report.getReportId())) {
                throw ApiException.conflict("Báo cáo này không phải là nguồn tạo ra lệnh khóa tài khoản đang có hiệu lực.");
            }
            String previous = target.getStatus().name();
            target.setStatus(UserStatus.ACTIVE);
            addAction(report, admin, ReportTargetType.USER, target.getUserId(), ModerationActionType.USER_UNBAN,
                    previous, UserStatus.ACTIVE.name(), now);
            repo.flush(); // báo cáo vẫn RESOLVED; KHÔNG ghi đè handled_by/handled_at
        }
        return buildDetail(admin, report);
    }

    private AdminReportDetailResponse enforceProduct(User admin, Long reportId, String action) {
        Report report = repo.lockReport(reportId).orElseThrow(() -> ApiException.notFound(REPORT_NOT_FOUND));
        if (report.getTargetType() != ReportTargetType.PRODUCT) {
            throw ApiException.badRequest("Chỉ có thể thực hiện chế tài tin đăng trên báo cáo loại Tin đăng.");
        }
        Product product = repo.lockProduct(report.getTargetId())
                .orElseThrow(() -> ApiException.notFound("Tin đăng bị báo cáo không tồn tại."));
        LocalDateTime now = nowUtc();

        if ("PRODUCT_HIDE".equals(action)) {
            if (Boolean.TRUE.equals(product.getIsDeleted())) {
                throw ApiException.conflict("Không thể ẩn tin đăng đã bị xóa.");
            }
            if (report.getStatus() != ReportStatus.OPEN && report.getStatus() != ReportStatus.REVIEWING) {
                throw ApiException.conflict("Không thể ẩn tin đăng từ báo cáo đã ở trạng thái kết thúc '" + report.getStatus().name() + "'.");
            }
            if (product.getModerationStatus() == ProductModerationStatus.BLOCKED) {
                throw ApiException.conflict("Tin đăng đã bị quản trị viên ẩn trước đó.");
            }
            Long cancelledAuctionId = null;
            Optional<Auction> open = repo.findOpenAuctionByProduct(product.getProductId(), true);
            if (open.isPresent()) {
                cancelAuction(open.get());
                product.setListingType(ListingType.FIXED_PRICE);
                cancelledAuctionId = open.get().getAuctionId();
            }
            String previous = product.getModerationStatus().name();
            product.setModerationStatus(ProductModerationStatus.BLOCKED);
            addAction(report, admin, ReportTargetType.PRODUCT, product.getProductId(), ModerationActionType.PRODUCT_HIDE,
                    previous, ProductModerationStatus.BLOCKED.name(), now);
            report.setStatus(ReportStatus.RESOLVED);
            report.setHandledBy(admin.getUserId());
            report.setHandledAt(now);
            notifyReporterTerminal(report, true);
            repo.flush();
            if (cancelledAuctionId != null) {
                Long auctionId = cancelledAuctionId;
                AfterCommit.run("publish AUCTION_CANCELLED " + auctionId,
                        () -> auctionEventPublisher.publishAuctionCancelled(auctionId, Instant.now()));
            }
        } else {
            if (Boolean.TRUE.equals(product.getIsDeleted())) {
                throw ApiException.conflict("Không thể gỡ ẩn tin đăng đã bị xóa.");
            }
            if (product.getModerationStatus() != ProductModerationStatus.BLOCKED) {
                throw ApiException.conflict("Tin đăng không ở trạng thái bị quản trị viên ẩn.");
            }
            Optional<ModerationAction> latest = repo.latestActionForTarget(ReportTargetType.PRODUCT, product.getProductId(), null);
            if (latest.isEmpty() || latest.get().getActionType() != ModerationActionType.PRODUCT_HIDE
                    || !latest.get().getReportId().equals(report.getReportId())) {
                throw ApiException.conflict("Báo cáo này không phải là nguồn tạo ra lệnh ẩn tin đăng đang có hiệu lực.");
            }
            String previous = product.getModerationStatus().name();
            product.setModerationStatus(ProductModerationStatus.CLEAR);
            addAction(report, admin, ReportTargetType.PRODUCT, product.getProductId(), ModerationActionType.PRODUCT_RESTORE,
                    previous, ProductModerationStatus.CLEAR.name(), now);
            repo.flush(); // báo cáo vẫn RESOLVED
        }
        return buildDetail(admin, report);
    }

    private AdminReportDetailResponse enforceCapability(User admin, Long reportId, AdminEnforcementRequest req) {
        Report report = repo.lockReport(reportId).orElseThrow(() -> ApiException.notFound(REPORT_NOT_FOUND));
        Long subjectId = resolveEnforcementTargetUserId(report,
                "Chỉ có thể áp dụng chế tài quyền hạn trên báo cáo loại Người dùng hoặc Đơn hàng.");
        User target = repo.lockUser(subjectId).orElseThrow(() -> ApiException.notFound("Người dùng bị báo cáo không tồn tại."));
        if (target.getRole() == UserRole.ADMIN || target.getUserId().equals(admin.getUserId())) {
            throw ApiException.forbidden(ADMIN_PROTECTED);
        }
        LocalDateTime now = nowUtc();
        List<String> caps = new ArrayList<>(new LinkedHashSet<>(req.capabilities()));

        if ("CAPABILITY_RESTRICT".equals(req.action())) {
            if (target.getRole() != UserRole.STUDENT) {
                throw ApiException.forbidden("Không thể áp dụng chế tài lên tài khoản không phải sinh viên.");
            }
            if (target.getStatus() == UserStatus.BANNED) {
                throw ApiException.conflict("Tài khoản người dùng đang bị khóa, không thể giới hạn quyền.");
            }
            if (target.getStatus() == UserStatus.INACTIVE) {
                throw ApiException.conflict("Không thể áp dụng chế tài lên tài khoản đang ngừng hoạt động.");
            }
            if (report.getStatus() != ReportStatus.OPEN && report.getStatus() != ReportStatus.REVIEWING) {
                throw ApiException.conflict("Không thể áp dụng chế tài từ báo cáo đã ở trạng thái kết thúc '" + report.getStatus().name() + "'.");
            }
            List<UserCapabilityRestriction> existing = repo.activeRestrictionsForCapabilities(target.getUserId(), caps, now);
            if (!existing.isEmpty()) {
                throw ApiException.conflict("Người dùng đã bị giới hạn quyền: "
                        + String.join(", ", existing.stream().map(UserCapabilityRestriction::getCapability).toList()) + ".");
            }
            LocalDateTime expiresAt = now.plusDays(req.durationDays());

            List<Long> cancelledAuctions = new ArrayList<>();
            if (caps.contains("SELL")) {
                for (Auction a : repo.lockOpenAuctionsBySeller(target.getUserId())) {
                    cancelAuction(a);
                    repo.lockProduct(a.getProductId()).ifPresent(p -> p.setListingType(ListingType.FIXED_PRICE));
                    cancelledAuctions.add(a.getAuctionId());
                }
            }

            for (String cap : caps) {
                UserCapabilityRestriction r = new UserCapabilityRestriction();
                r.setUserId(target.getUserId());
                r.setSourceReportId(report.getReportId());
                r.setImposedBy(admin.getUserId());
                r.setCapability(cap);
                r.setStartsAt(now);
                r.setExpiresAt(expiresAt);
                repo.persist(r);
                addAction(report, admin, ReportTargetType.USER, target.getUserId(),
                        "BUY".equals(cap) ? ModerationActionType.BUY_RESTRICT : ModerationActionType.SELL_RESTRICT,
                        "UNRESTRICTED", "RESTRICTED", now);
            }
            report.setStatus(ReportStatus.RESOLVED);
            report.setHandledBy(admin.getUserId());
            report.setHandledAt(now);

            notifyCapability(target.getUserId(), report.getReportId(), caps, true, expiresAt);
            notifyReporterTerminal(report, true);
            repo.flush();
            for (Long auctionId : cancelledAuctions) {
                AfterCommit.run("publish AUCTION_CANCELLED " + auctionId,
                        () -> auctionEventPublisher.publishAuctionCancelled(auctionId, Instant.now()));
            }
        } else {
            for (String cap : caps) {
                UserCapabilityRestriction r = repo.lockEffectiveBySourceReport(report.getReportId(), target.getUserId(), cap, now)
                        .orElseThrow(() -> ApiException.conflict(
                                "Báo cáo này không có quyền hạn '" + cap + "' đang có hiệu lực để khôi phục."));
                r.setRevokedAt(now);
                r.setRevokedBy(admin.getUserId());
                addAction(report, admin, ReportTargetType.USER, target.getUserId(),
                        "BUY".equals(cap) ? ModerationActionType.BUY_RESTORE : ModerationActionType.SELL_RESTORE,
                        "RESTRICTED", "UNRESTRICTED", now);
            }
            notifyCapability(target.getUserId(), report.getReportId(), caps, false, null);
            repo.flush(); // báo cáo vẫn RESOLVED; KHÔNG ghi đè handled_by/handled_at
        }
        return buildDetail(admin, report);
    }

    // =================================================================== lịch sử theo đối tượng

    public SubjectReportHistoryResponse subjectHistory(Long subjectUserId, int limit, String cursor,
                                                       Long excludeReportId, String type) {
        if (limit < 1 || limit > 20) {
            throw unprocessable("limit phải nằm trong khoảng 1..20.");
        }
        if (!"RECEIVED".equals(type) && !"SENT".equals(type)) {
            throw unprocessable("type chỉ chấp nhận RECEIVED hoặc SENT.");
        }
        if (!userRepository.existsById(subjectUserId)) {
            throw ApiException.notFound("Người dùng không tồn tại.");
        }
        LocalDateTime cursorAt = null;
        Long cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            Object[] c = ReportCursor.decode(cursor);
            cursorAt = (LocalDateTime) c[0];
            cursorId = (Long) c[1];
        }

        List<Report> fetched;
        long total;
        if ("SENT".equals(type)) {
            fetched = repo.listSentReports(subjectUserId, limit + 1, cursorAt, cursorId);
            total = repo.countSentReports(subjectUserId);
        } else {
            fetched = repo.listSubjectReports(subjectUserId, limit + 1, cursorAt, cursorId, excludeReportId);
            total = repo.countSubjectReports(subjectUserId, excludeReportId);
        }
        boolean hasMore = fetched.size() > limit;
        List<Report> page = hasMore ? fetched.subList(0, limit) : fetched;
        List<Long> ids = page.stream().map(Report::getReportId).toList();
        Map<Long, Long> evidence = repo.countAttachments(ids);
        Map<Long, List<ModerationAction>> actions = repo.listActionsByReports(ids);

        List<SubjectReportHistoryItem> items = page.stream().map(r -> new SubjectReportHistoryItem(
                r.getReportId(), r.getTargetType().name(), r.getReason().name(), r.getStatus().name(),
                r.getCreatedAt(), r.getHandledAt(), evidence.getOrDefault(r.getReportId(), 0L),
                actions.getOrDefault(r.getReportId(), List.of()).stream()
                        .map(a -> new SubjectReportHistoryEnforcementAction(a.getActionType().name(),
                                a.getPreviousStatus(), a.getNewStatus())).toList())).toList();
        String next = hasMore && !page.isEmpty()
                ? ReportCursor.encode(page.get(page.size() - 1).getCreatedAt(), page.get(page.size() - 1).getReportId())
                : null;
        return new SubjectReportHistoryResponse(subjectUserId, items, next, hasMore, total);
    }

    // =================================================================== bằng chứng (admin)

    public record EvidenceTarget(String url, String mimeType, String originalName, boolean video) {}

    public EvidenceTarget resolveEvidence(Long reportId, Long attachmentId, String variant) {
        repo.findReport(reportId).orElseThrow(() -> ApiException.notFound(REPORT_NOT_FOUND));
        ReportAttachment att = repo.findAttachment(reportId, attachmentId)
                .orElseThrow(() -> ApiException.notFound("Tệp minh chứng không tồn tại."));
        boolean video = att.getMimeType() != null && att.getMimeType().startsWith("video/");
        String url = att.getUrl();
        if (!video) {
            if (!"thumbnail".equals(variant) && !"full".equals(variant)) {
                throw unprocessable("Biến thể ảnh '" + variant + "' không hợp lệ. Chỉ chấp nhận 'thumbnail' hoặc 'full'.");
            }
            if ("thumbnail".equals(variant)) {
                url = MediaStorage.MediaUrls.buildTransformedUrl(att.getUrl(), 400);
            }
        }
        return new EvidenceTarget(url, att.getMimeType(), att.getOriginalName(), video);
    }

    // =================================================================== helpers: đối tượng

    private record Subject(User user, String roleInOrder) {}

    private Subject resolveSubject(Report report) {
        if (report.getTargetType() == ReportTargetType.USER) {
            return new Subject(userRepository.findById(report.getTargetId()).orElse(null), null);
        }
        if (report.getTargetType() == ReportTargetType.ORDER) {
            Order order = orderRepository.findById(report.getTargetId()).orElse(null);
            if (order == null) {
                return new Subject(null, null);
            }
            if (report.getReporterId().equals(order.getBuyerId())) {
                return new Subject(userRepository.findById(order.getSellerId()).orElse(null), "SELLER");
            }
            if (report.getReporterId().equals(order.getSellerId())) {
                return new Subject(userRepository.findById(order.getBuyerId()).orElse(null), "BUYER");
            }
        }
        return new Subject(null, null);
    }

    private Long resolveEnforcementTargetUserId(Report report, String wrongTypeMessage) {
        if (report.getTargetType() == ReportTargetType.USER) {
            return report.getTargetId();
        }
        if (report.getTargetType() == ReportTargetType.ORDER) {
            Order order = orderRepository.findById(report.getTargetId())
                    .orElseThrow(() -> ApiException.notFound("Đơn hàng không tồn tại."));
            if (report.getReporterId().equals(order.getBuyerId())) {
                return order.getSellerId();
            }
            if (report.getReporterId().equals(order.getSellerId())) {
                return order.getBuyerId();
            }
            throw ApiException.badRequest("Không thể xác định đối tượng liên quan trong đơn hàng.");
        }
        throw ApiException.badRequest(wrongTypeMessage);
    }

    private List<String> availableEnforcementActions(Report report, User admin) {
        LocalDateTime now = nowUtc();
        if (report.getTargetType() == ReportTargetType.USER || report.getTargetType() == ReportTargetType.ORDER) {
            User subject = resolveSubject(report).user();
            if (subject == null || subject.getRole() == UserRole.ADMIN || subject.getUserId().equals(admin.getUserId())) {
                return List.of();
            }
            List<String> actions = new ArrayList<>();
            if (!repo.activeRestrictionsBySourceReport(subject.getUserId(), report.getReportId(), now).isEmpty()) {
                actions.add("CAPABILITY_RESTORE");
            }
            if (subject.getStatus() == UserStatus.ACTIVE) {
                if (report.getStatus() == ReportStatus.OPEN || report.getStatus() == ReportStatus.REVIEWING) {
                    actions.add("USER_BAN");
                    Set<String> restricted = new HashSet<>();
                    repo.activeRestrictions(subject.getUserId(), now).forEach(r -> restricted.add(r.getCapability()));
                    if (restricted.size() < 2) {
                        actions.add("CAPABILITY_RESTRICT");
                    }
                }
            } else if (subject.getStatus() == UserStatus.BANNED) {
                Optional<ModerationAction> latest = repo.latestActionForTarget(ReportTargetType.USER, subject.getUserId(),
                        List.of(ModerationActionType.USER_BAN, ModerationActionType.USER_UNBAN));
                if (latest.isPresent() && latest.get().getActionType() == ModerationActionType.USER_BAN
                        && latest.get().getReportId().equals(report.getReportId())) {
                    actions.add("USER_UNBAN");
                }
            }
            return actions;
        }
        if (report.getTargetType() == ReportTargetType.PRODUCT) {
            Product product = repo.findProducts(List.of(report.getTargetId())).stream().findFirst().orElse(null);
            if (product == null || Boolean.TRUE.equals(product.getIsDeleted())) {
                return List.of();
            }
            if (product.getModerationStatus() == ProductModerationStatus.CLEAR) {
                return report.getStatus() == ReportStatus.OPEN || report.getStatus() == ReportStatus.REVIEWING
                        ? List.of("PRODUCT_HIDE") : List.of();
            }
            Optional<ModerationAction> latest = repo.latestActionForTarget(ReportTargetType.PRODUCT, product.getProductId(), null);
            if (latest.isPresent() && latest.get().getActionType() == ModerationActionType.PRODUCT_HIDE
                    && latest.get().getReportId().equals(report.getReportId())) {
                return List.of("PRODUCT_RESTORE");
            }
        }
        return List.of();
    }

    // =================================================================== helpers: dựng response

    private record TargetInfo(boolean available, Map<String, Object> summary) {}

    private static String targetKey(Report r) {
        return r.getTargetType().name() + ":" + r.getTargetId();
    }

    private Map<String, TargetInfo> resolveTargets(List<Report> reports) {
        Map<String, TargetInfo> result = new HashMap<>();
        Set<Long> userIds = new HashSet<>();
        Set<Long> productIds = new HashSet<>();
        Set<Long> orderIds = new HashSet<>();
        for (Report r : reports) {
            switch (r.getTargetType()) {
                case USER -> userIds.add(r.getTargetId());
                case PRODUCT -> productIds.add(r.getTargetId());
                case ORDER -> orderIds.add(r.getTargetId());
            }
        }
        for (User u : repo.findUsers(userIds)) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("user_id", u.getUserId());
            s.put("full_name", u.getFullName());
            s.put("avatar_url", u.getAvatarUrl());
            s.put("status", u.getStatus().name());
            s.put("role", u.getRole().name());
            result.put("USER:" + u.getUserId(), new TargetInfo(true, s));
        }
        List<Product> products = repo.findProducts(productIds);
        Map<Long, User> participants = usersById(products.stream().map(p -> p.getSeller().getUserId()).toList());
        for (Product p : products) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("product_id", p.getProductId());
            s.put("title", p.getTitle());
            User seller = participants.get(p.getSeller().getUserId());
            s.put("seller", seller == null ? null : new AdminHandlerSummary(seller.getUserId(), seller.getFullName()));
            s.put("status", p.getStatus().name());
            s.put("moderation_status", p.getModerationStatus().name());
            s.put("is_deleted", Boolean.TRUE.equals(p.getIsDeleted()));
            result.put("PRODUCT:" + p.getProductId(), new TargetInfo(true, s));
        }
        List<Order> orders = repo.findOrders(orderIds);
        List<Long> orderUserIds = new ArrayList<>();
        orders.forEach(o -> {
            orderUserIds.add(o.getBuyerId());
            orderUserIds.add(o.getSellerId());
        });
        Map<Long, User> orderUsers = usersById(orderUserIds);
        for (Order o : orders) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("order_id", o.getOrderId());
            s.put("order_code", o.getOrderCode());
            s.put("buyer", summary(orderUsers.get(o.getBuyerId())));
            s.put("seller", summary(orderUsers.get(o.getSellerId())));
            s.put("status", o.getStatus().name());
            s.put("total_amount", o.getTotalAmount() == null ? null : o.getTotalAmount().longValue());
            result.put("ORDER:" + o.getOrderId(), new TargetInfo(true, s));
        }
        for (Report r : reports) {
            result.putIfAbsent(targetKey(r), new TargetInfo(false, null));
        }
        return result;
    }

    private static AdminHandlerSummary summary(User u) {
        return u == null ? null : new AdminHandlerSummary(u.getUserId(), u.getFullName());
    }

    private Map<Long, User> usersById(Collection<Long> ids) {
        Map<Long, User> map = new HashMap<>();
        repo.findUsers(new HashSet<>(ids)).forEach(u -> map.put(u.getUserId(), u));
        return map;
    }

    private AdminReportResponse toResponse(Report r, TargetInfo target, long evidenceCount) {
        ReporterSummary reporter = null;
        if (r.getReporter() != null) {
            reporter = new ReporterSummary(r.getReporter().getUserId(), r.getReporter().getFullName(), r.getReporter().getAvatarUrl());
        }
        AdminHandlerSummary handler = r.getHandler() == null ? null
                : new AdminHandlerSummary(r.getHandler().getUserId(), r.getHandler().getFullName());
        String workflow = switch (r.getStatus()) {
            case OPEN -> "OPEN";
            case REVIEWING -> "REVIEWING";
            case RESOLVED, REJECTED -> "COMPLETED";
        };
        String conclusion = switch (r.getStatus()) {
            case RESOLVED -> ReportConclusion.SUPPORTED.name();
            case REJECTED -> ReportConclusion.INSUFFICIENT_EVIDENCE.name();
            default -> null;
        };
        return new AdminReportResponse(r.getReportId(), r.getReporterId(), reporter, r.getTargetType().name(),
                r.getTargetId(), target.available(), target.summary(), r.getReason().name(), r.getDescription(),
                r.getStatus().name(), workflow, conclusion, r.getHandledBy(), handler, r.getHandledAt(),
                r.getCreatedAt(), evidenceCount);
    }

    private static List<String> moderationOptions(ReportStatus status) {
        return switch (status) {
            case OPEN -> List.of("START_REVIEW", "CONFIRM_SUPPORTED", "MARK_INSUFFICIENT_EVIDENCE");
            case REVIEWING -> List.of("CONFIRM_SUPPORTED", "MARK_INSUFFICIENT_EVIDENCE");
            default -> List.of();
        };
    }

    private static ReportEvidenceMetadata toEvidence(ReportAttachment a) {
        boolean video = a.getMimeType() != null && a.getMimeType().startsWith("video/");
        return new ReportEvidenceMetadata(a.getAttachmentId(), a.getOriginalName(), a.getMimeType(), a.getSizeBytes(),
                a.getCreatedAt(), video ? "VIDEO" : "IMAGE");
    }

    // =================================================================== helpers: ghi

    private void addAction(Report report, User admin, ReportTargetType targetType, Long targetId,
                           ModerationActionType type, String previous, String next, LocalDateTime at) {
        repo.persist(ModerationAction.builder()
                .reportId(report.getReportId())
                .adminId(admin.getUserId())
                .targetType(targetType)
                .targetId(targetId)
                .actionType(type)
                .previousStatus(previous)
                .newStatus(next)
                .createdAt(at)
                .build());
    }

    private void cancelAuction(Auction a) {
        a.setStatus(AuctionStatus.CANCELLED);
        a.setWinnerId(null);
        a.setFinalPrice(null);
        a.setWinnerCheckoutDeadline(null);
    }

    private void notifyReporterTerminal(Report report, boolean resolved) {
        String title = resolved ? "Báo cáo của bạn đã được xử lý" : "Báo cáo của bạn đã được xem xét";
        String body = resolved
                ? "Báo cáo vi phạm của bạn đã được quản trị viên xem xét và xử lý."
                : "Báo cáo vi phạm của bạn đã được quản trị viên xem xét và kết luận không có vi phạm.";
        notificationWriter.create(report.getReporterId(), NotificationType.SYSTEM, title, body, "REPORT", report.getReportId());
    }

    private void notifyCapability(Long userId, Long reportId, List<String> caps, boolean restrict, LocalDateTime expiresAt) {
        String desc = caps.size() > 1 ? "mua hàng và bán hàng" : (caps.contains("BUY") ? "mua hàng" : "bán hàng");
        String title;
        String body;
        if (restrict) {
            title = "Quyền tài khoản của bạn đã được cập nhật";
            body = "Quyền " + desc + " của bạn bị tạm hạn chế đến " + (expiresAt == null ? "" : expiresAt.format(DMY)) + ".";
        } else {
            title = "Quyền tài khoản của bạn đã được khôi phục";
            body = "Quyền " + desc + " của bạn đã được khôi phục.";
        }
        notificationWriter.create(userId, NotificationType.SYSTEM, title, body, "ACCOUNT_ACCESS", reportId);
    }

    private static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static ApiException unprocessable(String msg) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, msg);
    }

    /** Cursor Python: base64url("<created_at iso>|<report_id>"). */
    static final class ReportCursor {
        private ReportCursor() {}

        static String encode(LocalDateTime createdAt, Long reportId) {
            return Base64.getUrlEncoder().encodeToString((createdAt + "|" + reportId).getBytes(StandardCharsets.UTF_8));
        }

        static Object[] decode(String cursor) {
            try {
                String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                String[] parts = raw.split("\\|");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Invalid cursor format");
                }
                return new Object[]{LocalDateTime.parse(parts[0]), Long.parseLong(parts[1])};
            } catch (RuntimeException e) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid pagination cursor.");
            }
        }
    }
}
