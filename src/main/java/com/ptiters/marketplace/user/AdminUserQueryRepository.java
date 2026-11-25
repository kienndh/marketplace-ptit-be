package com.ptiters.marketplace.user;

import com.ptiters.marketplace.product.ProductModerationStatus;
import com.ptiters.marketplace.product.ProductStatus;
import com.ptiters.marketplace.report.ModerationAction;
import com.ptiters.marketplace.report.ModerationActionType;
import com.ptiters.marketplace.report.Report;
import com.ptiters.marketplace.report.ReportTargetType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;

/** Truy vấn admin users (tương ứng {@code users/admin_repository.py}). Enum luôn qua setParameter. */
@Repository
public class AdminUserQueryRepository {

    @PersistenceContext
    private EntityManager em;

    private static final String ACTIVE_R = "r.revokedAt is null and r.startsAt <= :now and (r.expiresAt is null or r.expiresAt > :now)";

    public AdminUserDtos.SummaryStats stats(LocalDateTime now) {
        long total = em.createQuery("select count(u) from User u", Long.class).getSingleResult();
        long active = countByStatus(UserStatus.ACTIVE);
        long banned = countByStatus(UserStatus.BANNED);
        long restricted = em.createQuery("select count(distinct r.userId) from UserCapabilityRestriction r where " + ACTIVE_R, Long.class)
                .setParameter("now", now).getSingleResult();
        return new AdminUserDtos.SummaryStats(total, active, restricted, banned);
    }

    private long countByStatus(UserStatus s) {
        return em.createQuery("select count(u) from User u where u.status = :s", Long.class).setParameter("s", s).getSingleResult();
    }

    public record Page(List<User> users, long total) {}

    public Page listUsers(int page, int pageSize, String search, UserStatus status, UserRole role,
                          String capability, LocalDateTime now) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        Map<String, Object> params = new HashMap<>();
        if (status != null) {
            where.append(" and u.status = :status");
            params.put("status", status);
        }
        if (role != null) {
            where.append(" and u.role = :role");
            params.put("role", role);
        }
        if (capability != null) {
            where.append(" and u.userId in (select r.userId from UserCapabilityRestriction r where r.capability = :cap and " + ACTIVE_R + ")");
            params.put("cap", capability);
            params.put("now", now);
        }
        if (search != null && !search.isBlank()) {
            String s = search.strip();
            StringBuilder c = new StringBuilder("lower(u.fullName) like :like or lower(u.email) like :like");
            params.put("like", "%" + s.toLowerCase() + "%");
            if (s.chars().allMatch(Character::isDigit) && s.length() <= 18) {
                c.append(" or u.userId = :uid");
                params.put("uid", Long.parseLong(s));
            }
            try {
                params.put("pub", UUID.fromString(s));
                c.append(" or u.publicId = :pub");
            } catch (IllegalArgumentException ignored) {
                // không phải UUID
            }
            where.append(" and (").append(c).append(")");
        }
        var count = em.createQuery("select count(u) from User u" + where, Long.class);
        var items = em.createQuery("select u from User u" + where + " order by u.createdAt desc, u.userId desc", User.class);
        params.forEach((k, v) -> {
            count.setParameter(k, v);
            items.setParameter(k, v);
        });
        long total = count.getSingleResult();
        List<User> users = items.setFirstResult((page - 1) * pageSize).setMaxResults(pageSize).getResultList();
        return new Page(users, total);
    }

    public Map<Long, List<String>> activeRestrictions(Collection<Long> ids, LocalDateTime now) {
        Map<Long, List<String>> map = new HashMap<>();
        ids.forEach(i -> map.put(i, new ArrayList<>()));
        if (ids.isEmpty()) {
            return map;
        }
        List<Object[]> rows = em.createQuery("select r.userId, r.capability from UserCapabilityRestriction r where r.userId in :ids and "
                + ACTIVE_R, Object[].class).setParameter("ids", ids).setParameter("now", now).getResultList();
        for (Object[] row : rows) {
            List<String> l = map.get((Long) row[0]);
            if (!l.contains((String) row[1])) {
                l.add((String) row[1]);
            }
        }
        return map;
    }

    public Map<Long, Long> subjectReportCounts(Collection<Long> ids) {
        Map<Long, Long> map = new HashMap<>();
        ids.forEach(i -> map.put(i, 0L));
        if (ids.isEmpty()) {
            return map;
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                select s, count(*) from (
                  select r.target_id as s, r.report_id from reports r
                    where r.target_type = 'USER' and r.target_id in (:ids) and r.reporter_id <> r.target_id
                  union
                  select p.seller_id, r.report_id from reports r join products p on r.target_type = 'PRODUCT' and r.target_id = p.product_id
                    where p.seller_id in (:ids) and r.reporter_id <> p.seller_id
                  union
                  select o.seller_id, r.report_id from reports r join orders o on r.target_type = 'ORDER' and r.target_id = o.order_id
                    where o.seller_id in (:ids) and r.reporter_id = o.buyer_id
                  union
                  select o.buyer_id, r.report_id from reports r join orders o on r.target_type = 'ORDER' and r.target_id = o.order_id
                    where o.buyer_id in (:ids) and r.reporter_id = o.seller_id
                ) t group by s
                """).setParameter("ids", ids).getResultList();
        for (Object[] row : rows) {
            map.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return map;
    }

    public long countSupportedSubjectReports(Long userId) {
        Query q = em.createNativeQuery("""
                select count(*) from (
                  select r.report_id from reports r where r.target_type = 'USER' and r.target_id = :uid
                    and r.reporter_id <> :uid and r.status = 'RESOLVED'
                  union
                  select r.report_id from reports r join products p on r.target_type = 'PRODUCT' and r.target_id = p.product_id
                    where p.seller_id = :uid and r.reporter_id <> :uid and r.status = 'RESOLVED'
                  union
                  select r.report_id from reports r join orders o on r.target_type = 'ORDER' and r.target_id = o.order_id
                    where o.seller_id = :uid and r.reporter_id = o.buyer_id and r.status = 'RESOLVED'
                  union
                  select r.report_id from reports r join orders o on r.target_type = 'ORDER' and r.target_id = o.order_id
                    where o.buyer_id = :uid and r.reporter_id = o.seller_id and r.status = 'RESOLVED'
                ) t
                """).setParameter("uid", userId);
        return ((Number) q.getSingleResult()).longValue();
    }

    public long countSentReports(Long userId) {
        return em.createQuery("select count(r) from Report r where r.reporterId = :uid", Long.class)
                .setParameter("uid", userId).getSingleResult();
    }

    public long activeListingCount(Long userId) {
        return em.createQuery("select count(p) from Product p where p.seller.userId = :uid and p.status = :st and p.moderationStatus = :ms", Long.class)
                .setParameter("uid", userId).setParameter("st", ProductStatus.AVAILABLE)
                .setParameter("ms", ProductModerationStatus.CLEAR).getSingleResult();
    }

    public List<UserCapabilityRestriction> allRestrictions(Long userId) {
        return em.createQuery("select r from UserCapabilityRestriction r where r.userId = :uid order by r.startsAt desc", UserCapabilityRestriction.class)
                .setParameter("uid", userId).getResultList();
    }

    /** Báo cáo OPEN/REVIEWING nhắm vào người dùng (trực tiếp hoặc qua đơn hàng). */
    public List<Report> eligibleReports(Long userId) {
        @SuppressWarnings("unchecked")
        List<Number> ids = em.createNativeQuery("""
                select r.report_id from reports r
                left join orders o on r.target_type = 'ORDER' and r.target_id = o.order_id
                where r.status in ('OPEN', 'REVIEWING') and (
                    (r.target_type = 'USER' and r.target_id = :uid and r.reporter_id <> :uid)
                    or (r.target_type = 'ORDER' and (
                        (o.seller_id = :uid and r.reporter_id = o.buyer_id)
                     or (o.buyer_id = :uid and r.reporter_id = o.seller_id))))
                order by r.created_at desc, r.report_id desc
                """).setParameter("uid", userId).getResultList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Long> idList = ids.stream().map(Number::longValue).toList();
        Map<Long, Report> byId = new HashMap<>();
        em.createQuery("select r from Report r where r.reportId in :ids", Report.class).setParameter("ids", idList)
                .getResultList().forEach(r -> byId.put(r.getReportId(), r));
        return idList.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    public List<ModerationAction> moderationHistory(Long userId) {
        return em.createQuery("""
                select m from ModerationAction m
                where (m.targetType = :ut and m.targetId = :uid)
                   or (m.targetType = :pt and m.targetId in (select p.productId from Product p where p.seller.userId = :uid))
                order by m.createdAt desc, m.moderationActionId desc
                """, ModerationAction.class)
                .setParameter("ut", ReportTargetType.USER).setParameter("pt", ReportTargetType.PRODUCT)
                .setParameter("uid", userId).getResultList();
    }

    public Map<Long, User> usersByIds(Collection<Long> ids) {
        Map<Long, User> map = new HashMap<>();
        if (!ids.isEmpty()) {
            em.createQuery("select u from User u where u.userId in :ids", User.class).setParameter("ids", new HashSet<>(ids))
                    .getResultList().forEach(u -> map.put(u.getUserId(), u));
        }
        return map;
    }

    /** Tìm người dùng công khai (cho mời nhóm), giống Python {@code ConversationRepository.search_users}. */
    public List<User> searchActiveUsers(String query, int limit, Long excludeUserId, Long excludeConversationId) {
        String q = query == null ? "" : query.strip();
        if (q.length() < 2) {
            return List.of();
        }
        StringBuilder hql = new StringBuilder("select u from User u where u.status = :active and lower(u.fullName) like :like");
        if (excludeUserId != null) {
            hql.append(" and u.userId <> :me");
        }
        if (excludeConversationId != null) {
            hql.append(" and u.userId not in (select cm.userId from ConversationMember cm where cm.conversationId = :cid and cm.leftAt is null)");
        }
        var typed = em.createQuery(hql.toString(), User.class)
                .setParameter("active", UserStatus.ACTIVE).setParameter("like", "%" + q.toLowerCase() + "%");
        if (excludeUserId != null) {
            typed.setParameter("me", excludeUserId);
        }
        if (excludeConversationId != null) {
            typed.setParameter("cid", excludeConversationId);
        }
        return typed.setMaxResults(limit).getResultList();
    }
}
