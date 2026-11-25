package com.ptiters.marketplace.report;

import com.ptiters.marketplace.auction.Auction;
import com.ptiters.marketplace.auction.AuctionStatus;
import com.ptiters.marketplace.order.Order;
import com.ptiters.marketplace.product.Product;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserCapabilityRestriction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Truy vấn nghiệp vụ báo cáo / kiểm duyệt / chế tài (Track C). Tương ứng {@code reports/repository.py}
 * và phần truy vấn của {@code users/capability_repository.py}. Enum luôn truyền bằng {@code setParameter}.
 */
@Repository
public class ReportQueryRepository {

    @PersistenceContext
    private EntityManager em;

    // ------------------------------------------------------------------ report

    /** {@code SELECT ... FOR UPDATE} trên report (thứ tự khoá chuẩn: Report → User → Product → Auction). */
    public Optional<Report> lockReport(Long reportId) {
        return Optional.ofNullable(em.find(Report.class, reportId, LockModeType.PESSIMISTIC_WRITE));
    }

    public Optional<Report> findReport(Long reportId) {
        return Optional.ofNullable(em.find(Report.class, reportId));
    }

    public boolean hasActiveReport(Long reporterId, ReportTargetType type, Long targetId) {
        return !em.createQuery("""
                        select r.reportId from Report r
                        where r.reporterId = :reporter and r.targetType = :type and r.targetId = :target
                          and r.status in :statuses
                        """, Long.class)
                .setParameter("reporter", reporterId)
                .setParameter("type", type)
                .setParameter("target", targetId)
                .setParameter("statuses", List.of(ReportStatus.OPEN, ReportStatus.REVIEWING))
                .setMaxResults(1)
                .getResultList().isEmpty();
    }

    /** Hàng đợi admin: created_at DESC, report_id DESC; lấy {@code fetchSize} (= limit + 1). */
    public List<Report> listAdminReports(int fetchSize, LocalDateTime cursorAt, Long cursorId,
                                         List<ReportStatus> statuses, ReportTargetType targetType) {
        StringBuilder hql = new StringBuilder("select r from Report r where 1 = 1");
        if (statuses != null && !statuses.isEmpty()) {
            hql.append(" and r.status in :statuses");
        }
        if (targetType != null) {
            hql.append(" and r.targetType = :targetType");
        }
        if (cursorAt != null) {
            hql.append(" and (r.createdAt < :cAt or (r.createdAt = :cAt and r.reportId < :cId))");
        }
        hql.append(" order by r.createdAt desc, r.reportId desc");
        var q = em.createQuery(hql.toString(), Report.class);
        if (statuses != null && !statuses.isEmpty()) {
            q.setParameter("statuses", statuses);
        }
        if (targetType != null) {
            q.setParameter("targetType", targetType);
        }
        if (cursorAt != null) {
            q.setParameter("cAt", cursorAt).setParameter("cId", cursorId);
        }
        return q.setMaxResults(fetchSize).getResultList();
    }

    // ------------------------------------------------------------------ attachments

    public List<ReportAttachment> listAttachments(Long reportId) {
        return em.createQuery(
                        "select a from ReportAttachment a where a.report.reportId = :rid order by a.attachmentId asc",
                        ReportAttachment.class)
                .setParameter("rid", reportId).getResultList();
    }

    public Optional<ReportAttachment> findAttachment(Long reportId, Long attachmentId) {
        return em.createQuery(
                        "select a from ReportAttachment a where a.report.reportId = :rid and a.attachmentId = :aid",
                        ReportAttachment.class)
                .setParameter("rid", reportId).setParameter("aid", attachmentId)
                .getResultStream().findFirst();
    }

    public Map<Long, Long> countAttachments(Collection<Long> reportIds) {
        Map<Long, Long> result = new HashMap<>();
        if (reportIds.isEmpty()) {
            return result;
        }
        List<Object[]> rows = em.createQuery(
                        "select a.report.reportId, count(a) from ReportAttachment a where a.report.reportId in :ids group by a.report.reportId",
                        Object[].class)
                .setParameter("ids", reportIds).getResultList();
        for (Object[] row : rows) {
            result.put((Long) row[0], (Long) row[1]);
        }
        return result;
    }

    // ------------------------------------------------------------------ moderation actions

    public List<ModerationAction> listActions(Long reportId) {
        return em.createQuery(
                        "select m from ModerationAction m where m.reportId = :rid order by m.createdAt asc, m.moderationActionId asc",
                        ModerationAction.class)
                .setParameter("rid", reportId).getResultList();
    }

    public Map<Long, List<ModerationAction>> listActionsByReports(Collection<Long> reportIds) {
        Map<Long, List<ModerationAction>> map = new HashMap<>();
        for (Long id : reportIds) {
            map.put(id, new ArrayList<>());
        }
        if (reportIds.isEmpty()) {
            return map;
        }
        List<ModerationAction> all = em.createQuery(
                        "select m from ModerationAction m where m.reportId in :ids order by m.createdAt asc, m.moderationActionId asc",
                        ModerationAction.class)
                .setParameter("ids", reportIds).getResultList();
        for (ModerationAction m : all) {
            map.get(m.getReportId()).add(m);
        }
        return map;
    }

    public Optional<ModerationAction> latestActionForTarget(ReportTargetType type, Long targetId,
                                                            Collection<ModerationActionType> actionTypes) {
        StringBuilder hql = new StringBuilder(
                "select m from ModerationAction m where m.targetType = :type and m.targetId = :tid");
        if (actionTypes != null && !actionTypes.isEmpty()) {
            hql.append(" and m.actionType in :types");
        }
        hql.append(" order by m.createdAt desc, m.moderationActionId desc");
        var q = em.createQuery(hql.toString(), ModerationAction.class)
                .setParameter("type", type).setParameter("tid", targetId);
        if (actionTypes != null && !actionTypes.isEmpty()) {
            q.setParameter("types", actionTypes);
        }
        return q.setMaxResults(1).getResultStream().findFirst();
    }

    // ------------------------------------------------------------------ lịch sử báo cáo theo đối tượng

    private static final String SUBJECT_FROM = """
            from reports r
            left join products p on r.target_type = 'PRODUCT' and r.target_id = p.product_id
            left join orders o on r.target_type = 'ORDER' and r.target_id = o.order_id
            where r.reporter_id <> :uid and (
                (r.target_type = 'USER' and r.target_id = :uid)
                or (r.target_type = 'PRODUCT' and p.seller_id = :uid)
                or (r.target_type = 'ORDER' and (
                      (o.buyer_id = r.reporter_id and o.seller_id = :uid)
                   or (o.seller_id = r.reporter_id and o.buyer_id = :uid)))
            )
            """;

    public long countSubjectReports(Long userId, Long excludeReportId) {
        Query q = em.createNativeQuery("select count(r.report_id) " + SUBJECT_FROM
                + (excludeReportId != null ? " and r.report_id <> :excl" : ""));
        q.setParameter("uid", userId);
        if (excludeReportId != null) {
            q.setParameter("excl", excludeReportId);
        }
        return ((Number) q.getSingleResult()).longValue();
    }

    public List<Report> listSubjectReports(Long userId, int fetchSize, LocalDateTime cursorAt, Long cursorId,
                                           Long excludeReportId) {
        StringBuilder sql = new StringBuilder("select r.report_id " + SUBJECT_FROM);
        if (excludeReportId != null) {
            sql.append(" and r.report_id <> :excl");
        }
        if (cursorAt != null) {
            sql.append(" and (r.created_at < :cAt or (r.created_at = :cAt and r.report_id < :cId))");
        }
        sql.append(" order by r.created_at desc, r.report_id desc limit :lim");
        Query q = em.createNativeQuery(sql.toString()).setParameter("uid", userId).setParameter("lim", fetchSize);
        if (excludeReportId != null) {
            q.setParameter("excl", excludeReportId);
        }
        if (cursorAt != null) {
            q.setParameter("cAt", cursorAt).setParameter("cId", cursorId);
        }
        @SuppressWarnings("unchecked")
        List<Number> ids = q.getResultList();
        return loadReportsInOrder(ids.stream().map(Number::longValue).toList());
    }

    public long countSentReports(Long reporterId) {
        return em.createQuery("select count(r) from Report r where r.reporterId = :uid", Long.class)
                .setParameter("uid", reporterId).getSingleResult();
    }

    public List<Report> listSentReports(Long reporterId, int fetchSize, LocalDateTime cursorAt, Long cursorId) {
        StringBuilder hql = new StringBuilder("select r from Report r where r.reporterId = :uid");
        if (cursorAt != null) {
            hql.append(" and (r.createdAt < :cAt or (r.createdAt = :cAt and r.reportId < :cId))");
        }
        hql.append(" order by r.createdAt desc, r.reportId desc");
        var q = em.createQuery(hql.toString(), Report.class).setParameter("uid", reporterId);
        if (cursorAt != null) {
            q.setParameter("cAt", cursorAt).setParameter("cId", cursorId);
        }
        return q.setMaxResults(fetchSize).getResultList();
    }

    private List<Report> loadReportsInOrder(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Report> byId = new HashMap<>();
        em.createQuery("select r from Report r where r.reportId in :ids", Report.class)
                .setParameter("ids", ids).getResultList().forEach(r -> byId.put(r.getReportId(), r));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    // ------------------------------------------------------------------ đối tượng bị báo cáo

    public List<User> findUsers(Collection<Long> ids) {
        return ids.isEmpty() ? List.of()
                : em.createQuery("select u from User u where u.userId in :ids", User.class)
                .setParameter("ids", ids).getResultList();
    }

    public List<Product> findProducts(Collection<Long> ids) {
        return ids.isEmpty() ? List.of()
                : em.createQuery("select p from Product p where p.productId in :ids", Product.class)
                .setParameter("ids", ids).getResultList();
    }

    public List<Order> findOrders(Collection<Long> ids) {
        return ids.isEmpty() ? List.of()
                : em.createQuery("select o from Order o where o.orderId in :ids", Order.class)
                .setParameter("ids", ids).getResultList();
    }

    public Optional<User> lockUser(Long userId) {
        return Optional.ofNullable(em.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE));
    }

    public Optional<Product> lockProduct(Long productId) {
        return Optional.ofNullable(em.find(Product.class, productId, LockModeType.PESSIMISTIC_WRITE));
    }

    // ------------------------------------------------------------------ auction

    /** Đấu giá SCHEDULED/ACTIVE của sản phẩm (có thể khoá). */
    public Optional<Auction> findOpenAuctionByProduct(Long productId, boolean lock) {
        var q = em.createQuery("select a from Auction a where a.productId = :pid and a.status in :sts", Auction.class)
                .setParameter("pid", productId)
                .setParameter("sts", List.of(AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE))
                .setMaxResults(1);
        if (lock) {
            q.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        }
        return q.getResultStream().findFirst();
    }

    /** Mọi đấu giá SCHEDULED/ACTIVE của người bán, thứ tự khoá tất định: product_id ASC, auction_id ASC. */
    public List<Auction> lockOpenAuctionsBySeller(Long sellerId) {
        return em.createQuery("""
                        select a from Auction a, Product p
                        where a.productId = p.productId and p.seller.userId = :sid and a.status in :sts
                        order by p.productId asc, a.auctionId asc
                        """, Auction.class)
                .setParameter("sid", sellerId)
                .setParameter("sts", List.of(AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
    }

    /** Người thắng đấu giá còn quyền thanh toán (chưa có đơn, chưa quá hạn checkout). */
    public Optional<Auction> findLatestEndedAuctionWithActiveWinnerRight(Long productId, LocalDateTime nowUtc) {
        return em.createQuery("""
                        select a from Auction a
                        where a.productId = :pid and a.status = :ended
                          and a.winnerId is not null and a.finalPrice is not null
                          and a.winnerCheckoutDeadline is not null and a.winnerCheckoutDeadline >= :now
                          and not exists (select 1 from Order o where o.auctionId = a.auctionId)
                        order by a.auctionId desc
                        """, Auction.class)
                .setParameter("pid", productId)
                .setParameter("ended", AuctionStatus.ENDED)
                .setParameter("now", nowUtc)
                .setMaxResults(1).getResultStream().findFirst();
    }

    // ------------------------------------------------------------------ chế tài quyền (capability)

    private static final String ACTIVE_RESTRICTION = """
            r.revokedAt is null and r.startsAt <= :now and (r.expiresAt is null or r.expiresAt > :now)
            """;

    public List<UserCapabilityRestriction> activeRestrictions(Long userId, LocalDateTime nowUtc) {
        return em.createQuery("select r from UserCapabilityRestriction r where r.userId = :uid and "
                        + ACTIVE_RESTRICTION + " order by r.startsAt desc", UserCapabilityRestriction.class)
                .setParameter("uid", userId).setParameter("now", nowUtc).getResultList();
    }

    public List<UserCapabilityRestriction> activeRestrictionsForCapabilities(Long userId, Collection<String> caps,
                                                                             LocalDateTime nowUtc) {
        return em.createQuery("select r from UserCapabilityRestriction r where r.userId = :uid and r.capability in :caps and "
                        + ACTIVE_RESTRICTION, UserCapabilityRestriction.class)
                .setParameter("uid", userId).setParameter("caps", caps).setParameter("now", nowUtc).getResultList();
    }

    public List<UserCapabilityRestriction> activeRestrictionsBySourceReport(Long userId, Long reportId,
                                                                            LocalDateTime nowUtc) {
        return em.createQuery("select r from UserCapabilityRestriction r where r.userId = :uid and r.sourceReportId = :rid and "
                        + ACTIVE_RESTRICTION + " order by r.startsAt desc", UserCapabilityRestriction.class)
                .setParameter("uid", userId).setParameter("rid", reportId).setParameter("now", nowUtc).getResultList();
    }

    public Optional<UserCapabilityRestriction> lockEffectiveBySourceReport(Long reportId, Long userId, String capability,
                                                                           LocalDateTime nowUtc) {
        return em.createQuery("select r from UserCapabilityRestriction r where r.sourceReportId = :rid and r.userId = :uid "
                        + "and r.capability = :cap and " + ACTIVE_RESTRICTION, UserCapabilityRestriction.class)
                .setParameter("rid", reportId).setParameter("uid", userId).setParameter("cap", capability)
                .setParameter("now", nowUtc)
                .setMaxResults(1).setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultStream().findFirst();
    }

    public void persist(Object entity) {
        em.persist(entity);
    }

    public void flush() {
        em.flush();
    }
}
