package com.ptiters.marketplace.communitymoment;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class CommunityMomentQueryRepository {

    public static final String COMMUNITY_HERO_LOCK_NAMESPACE = "community_hero_lifecycle";

    public record PaginatedMoments(List<CommunityMoment> items, boolean hasMore, String nextCursor) {
    }

    @PersistenceContext
    private EntityManager em;

    public void acquireHeroAdvisoryLock() {
        em.createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:lockName))) AS _lock")
                .setParameter("lockName", COMMUNITY_HERO_LOCK_NAMESPACE)
                .getSingleResult();
    }

    public boolean hasPendingByUploaderId(Long uploaderId) {
        Long count = em.createQuery(
                        "SELECT COUNT(m) FROM CommunityMoment m " +
                        "WHERE m.uploaderId = :uploaderId AND m.status = :status",
                        Long.class)
                .setParameter("uploaderId", uploaderId)
                .setParameter("status", CommunityMomentStatus.PENDING)
                .getSingleResult();
        return count != null && count > 0;
    }

    public List<CommunityMoment> findLatestApprovedForHero(int limit) {
        List<CommunityMoment> rows = em.createQuery(
                        "SELECT m FROM CommunityMoment m " +
                        "WHERE m.status = :status " +
                        "ORDER BY m.reviewedAt DESC, m.momentId DESC",
                        CommunityMoment.class)
                .setParameter("status", CommunityMomentStatus.APPROVED)
                .setMaxResults(limit)
                .getResultList();
        List<CommunityMoment> ordered = new ArrayList<>(rows);
        // Return retained approved moments chronologically (oldest -> newest) so newest sits on the right.
        Collections.reverse(ordered);
        return ordered;
    }

    public Optional<CommunityMoment> findByIdWithRelations(Long momentId) {
        List<CommunityMoment> results = em.createQuery(
                        "SELECT m FROM CommunityMoment m " +
                        "JOIN FETCH m.uploader " +
                        "LEFT JOIN FETCH m.reviewer " +
                        "WHERE m.momentId = :momentId",
                        CommunityMoment.class)
                .setParameter("momentId", momentId)
                .getResultList();
        return results.stream().findFirst();
    }

    public Optional<CommunityMoment> findByIdForUpdate(Long momentId) {
        List<CommunityMoment> results = em.createQuery(
                        "SELECT m FROM CommunityMoment m WHERE m.momentId = :momentId",
                        CommunityMoment.class)
                .setParameter("momentId", momentId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        return results.stream().findFirst();
    }

    public List<CommunityMoment> findAllApprovedOrderByReviewedDescForUpdate() {
        return em.createQuery(
                        "SELECT m FROM CommunityMoment m " +
                        "WHERE m.status = :status " +
                        "ORDER BY m.reviewedAt DESC, m.momentId DESC",
                        CommunityMoment.class)
                .setParameter("status", CommunityMomentStatus.APPROVED)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
    }

    public PaginatedMoments listAdminMomentsCursor(
            CommunityMomentStatus status,
            LocalDateTime cursorCreatedAt,
            Long cursorMomentId,
            int limit
    ) {
        StringBuilder jpql = new StringBuilder(
                "SELECT m FROM CommunityMoment m " +
                "JOIN FETCH m.uploader " +
                "LEFT JOIN FETCH m.reviewer " +
                "WHERE 1=1 "
        );
        Map<String, Object> params = new HashMap<>();

        if (status != null) {
            jpql.append("AND m.status = :status ");
            params.put("status", status);
        }

        if (cursorCreatedAt != null && cursorMomentId != null) {
            jpql.append("AND (m.createdAt < :cursorCreatedAt OR (m.createdAt = :cursorCreatedAt AND m.momentId < :cursorMomentId)) ");
            params.put("cursorCreatedAt", cursorCreatedAt);
            params.put("cursorMomentId", cursorMomentId);
        }

        jpql.append("ORDER BY m.createdAt DESC, m.momentId DESC");

        TypedQuery<CommunityMoment> query = em.createQuery(jpql.toString(), CommunityMoment.class);
        params.forEach(query::setParameter);
        query.setMaxResults(limit + 1);

        List<CommunityMoment> rows = query.getResultList();
        boolean hasMore = rows.size() > limit;
        List<CommunityMoment> items = hasMore ? new ArrayList<>(rows.subList(0, limit)) : rows;

        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            CommunityMoment last = items.get(items.size() - 1);
            nextCursor = encodeCursor(last.getCreatedAt(), last.getMomentId());
        }

        return new PaginatedMoments(items, hasMore, nextCursor);
    }

    public static String encodeCursor(LocalDateTime createdAt, Long momentId) {
        if (createdAt == null || momentId == null) {
            return null;
        }
        String payload = createdAt.toString() + "|" + momentId;
        return Base64.getUrlEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }
}
