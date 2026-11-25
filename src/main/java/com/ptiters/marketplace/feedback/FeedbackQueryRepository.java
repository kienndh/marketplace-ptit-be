package com.ptiters.marketplace.feedback;

import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserAuthIdentity;
import com.ptiters.marketplace.user.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

@Repository
public class FeedbackQueryRepository {

    @PersistenceContext
    private EntityManager em;

    public List<SiteFeedback> listPublicFeedbacks(int limit, LocalDateTime cursorTime, Long cursorId) {
        StringBuilder jpql = new StringBuilder(
                "SELECT f FROM SiteFeedback f JOIN f.user u WHERE u.status = :activeStatus "
        );
        Map<String, Object> params = new HashMap<>();
        params.put("activeStatus", UserStatus.ACTIVE);

        if (cursorTime != null && cursorId != null) {
            jpql.append("AND (f.createdAt < :cursorTime OR (f.createdAt = :cursorTime AND f.feedbackId < :cursorId)) ");
            params.put("cursorTime", cursorTime);
            params.put("cursorId", cursorId);
        }
        jpql.append("ORDER BY f.createdAt DESC, f.feedbackId DESC");

        TypedQuery<SiteFeedback> query = em.createQuery(jpql.toString(), SiteFeedback.class);
        params.forEach(query::setParameter);
        query.setMaxResults(limit + 1);
        return query.getResultList();
    }

    public List<SiteFeedback> listUserFeedbacks(Long userId, int limit, LocalDateTime cursorTime, Long cursorId) {
        StringBuilder jpql = new StringBuilder(
                "SELECT f FROM SiteFeedback f WHERE f.userId = :userId "
        );
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);

        if (cursorTime != null && cursorId != null) {
            jpql.append("AND (f.createdAt < :cursorTime OR (f.createdAt = :cursorTime AND f.feedbackId < :cursorId)) ");
            params.put("cursorTime", cursorTime);
            params.put("cursorId", cursorId);
        }
        jpql.append("ORDER BY f.createdAt DESC, f.feedbackId DESC");

        TypedQuery<SiteFeedback> query = em.createQuery(jpql.toString(), SiteFeedback.class);
        params.forEach(query::setParameter);
        query.setMaxResults(limit + 1);
        return query.getResultList();
    }

    public List<SiteFeedback> listAdminFeedbacks(int limit, LocalDateTime cursorTime, Long cursorId) {
        StringBuilder jpql = new StringBuilder(
                "SELECT f FROM SiteFeedback f JOIN FETCH f.user u "
        );
        Map<String, Object> params = new HashMap<>();

        if (cursorTime != null && cursorId != null) {
            jpql.append("WHERE (f.createdAt < :cursorTime OR (f.createdAt = :cursorTime AND f.feedbackId < :cursorId)) ");
            params.put("cursorTime", cursorTime);
            params.put("cursorId", cursorId);
        }
        jpql.append("ORDER BY f.createdAt DESC, f.feedbackId DESC");

        TypedQuery<SiteFeedback> query = em.createQuery(jpql.toString(), SiteFeedback.class);
        params.forEach(query::setParameter);
        query.setMaxResults(limit + 1);
        return query.getResultList();
    }

    public Map<Long, List<SiteFeedbackAttachment>> findAttachmentsByFeedbackIds(List<Long> feedbackIds) {
        if (feedbackIds == null || feedbackIds.isEmpty()) {
            return Map.of();
        }
        List<SiteFeedbackAttachment> rows = em.createQuery(
                        "SELECT a FROM SiteFeedbackAttachment a WHERE a.feedback.feedbackId IN :ids ORDER BY a.attachmentId ASC",
                        SiteFeedbackAttachment.class
                )
                .setParameter("ids", feedbackIds)
                .getResultList();

        Map<Long, List<SiteFeedbackAttachment>> grouped = new LinkedHashMap<>();
        for (SiteFeedbackAttachment att : rows) {
            Long fbId = att.getFeedback().getFeedbackId();
            grouped.computeIfAbsent(fbId, k -> new ArrayList<>()).add(att);
        }
        return grouped;
    }

    public Optional<SiteFeedback> findFeedbackById(Long feedbackId, boolean forUpdate) {
        String jpql = forUpdate
                ? "SELECT f FROM SiteFeedback f WHERE f.feedbackId = :feedbackId"
                : "SELECT f FROM SiteFeedback f JOIN FETCH f.user WHERE f.feedbackId = :feedbackId";
        TypedQuery<SiteFeedback> query = em.createQuery(jpql, SiteFeedback.class)
                .setParameter("feedbackId", feedbackId);
        if (forUpdate) {
            query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        }
        List<SiteFeedback> results = query.getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<SiteFeedbackAttachment> findAttachment(Long feedbackId, Long attachmentId) {
        List<SiteFeedbackAttachment> results = em.createQuery(
                        "SELECT a FROM SiteFeedbackAttachment a WHERE a.attachmentId = :attId AND a.feedback.feedbackId = :fbId",
                        SiteFeedbackAttachment.class
                )
                .setParameter("attId", attachmentId)
                .setParameter("fbId", feedbackId)
                .getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<User> findUserByFirebaseProviderUid(String providerUid) {
        List<User> results = em.createQuery(
                        "SELECT u FROM UserAuthIdentity i JOIN i.user u WHERE i.provider = :provider AND i.providerUid = :uid",
                        User.class
                )
                .setParameter("provider", UserAuthIdentity.PROVIDER_FIREBASE)
                .setParameter("uid", providerUid)
                .getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<User> findUserByEmailIgnoreCase(String email) {
        List<User> results = em.createQuery(
                        "SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)",
                        User.class
                )
                .setParameter("email", email)
                .getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<SupporterLeaderboardItem> getSupporterLeaderboard(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 10));
        List<Object[]> rows = em.createQuery(
                        "SELECT u.publicId, u.fullName, u.avatarUrl, " +
                        "SUM(c.amountVnd), COUNT(c.contributionId), MAX(c.receivedAt) " +
                        "FROM SupportContribution c JOIN c.supporter u " +
                        "WHERE c.isPublic = true AND u.status = :activeStatus " +
                        "GROUP BY u.userId, u.publicId, u.fullName, u.avatarUrl " +
                        "ORDER BY SUM(c.amountVnd) DESC, MAX(c.receivedAt) ASC, u.userId ASC",
                        Object[].class
                )
                .setParameter("activeStatus", UserStatus.ACTIVE)
                .setMaxResults(safeLimit)
                .getResultList();

        List<SupporterLeaderboardItem> items = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            UUID publicId = (UUID) r[0];
            String fullName = (String) r[1];
            String avatarUrl = (String) r[2];
            long totalAmount = ((Number) r[3]).longValue();
            long count = ((Number) r[4]).longValue();
            OffsetDateTime lastSupportedAt = (OffsetDateTime) r[5];

            items.add(new SupporterLeaderboardItem(
                    i + 1,
                    new SupporterPublicIdentity(
                            publicId,
                            fullName,
                            avatarUrl,
                            "/profile/" + publicId
                    ),
                    totalAmount,
                    count,
                    lastSupportedAt
            ));
        }
        return items;
    }

    public List<User> searchSupportersForAdmin(String rawQuery, int limit) {
        String trimmed = rawQuery == null ? "" : rawQuery.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 20));
        StringBuilder jpql = new StringBuilder("SELECT u FROM User u WHERE u.status = :activeStatus ");
        Map<String, Object> params = new HashMap<>();
        params.put("activeStatus", UserStatus.ACTIVE);
        params.put("kw", "%" + trimmed.toLowerCase(Locale.ROOT) + "%");

        Long numericId = parsePositiveLongOrNull(trimmed);
        if (numericId != null) {
            jpql.append("AND (u.userId = :numericId OR LOWER(u.fullName) LIKE :kw) ");
            params.put("numericId", numericId);
        } else {
            jpql.append("AND LOWER(u.fullName) LIKE :kw ");
        }
        jpql.append("ORDER BY u.fullName ASC, u.userId ASC");

        TypedQuery<User> query = em.createQuery(jpql.toString(), User.class);
        params.forEach(query::setParameter);
        query.setMaxResults(safeLimit);
        return query.getResultList();
    }

    private static Long parsePositiveLongOrNull(String s) {
        if (!s.matches("\\d+")) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Optional<SupportContribution> findContributionById(Long contributionId) {
        List<SupportContribution> results = em.createQuery(
                        "SELECT c FROM SupportContribution c JOIN FETCH c.supporter JOIN FETCH c.recorder WHERE c.contributionId = :id",
                        SupportContribution.class
                )
                .setParameter("id", contributionId)
                .getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<SupportContribution> listAdminContributions(int limit, OffsetDateTime cursorTime, Long cursorId) {
        StringBuilder jpql = new StringBuilder(
                "SELECT c FROM SupportContribution c JOIN FETCH c.supporter JOIN FETCH c.recorder "
        );
        Map<String, Object> params = new HashMap<>();
        if (cursorTime != null && cursorId != null) {
            jpql.append("WHERE (c.receivedAt < :cursorTime OR (c.receivedAt = :cursorTime AND c.contributionId < :cursorId)) ");
            params.put("cursorTime", cursorTime);
            params.put("cursorId", cursorId);
        }
        jpql.append("ORDER BY c.receivedAt DESC, c.contributionId DESC");

        TypedQuery<SupportContribution> query = em.createQuery(jpql.toString(), SupportContribution.class);
        params.forEach(query::setParameter);
        query.setMaxResults(limit + 1);
        return query.getResultList();
    }

    public List<SupportClaim> listUserSupportClaims(Long supporterId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return em.createQuery(
                        "SELECT c FROM SupportClaim c WHERE c.supporterId = :supporterId ORDER BY c.createdAt DESC, c.claimId DESC",
                        SupportClaim.class
                )
                .setParameter("supporterId", supporterId)
                .setMaxResults(safeLimit)
                .getResultList();
    }

    public List<SupportClaim> listAdminSupportClaims(
            SupportClaimStatus statusFilter,
            int limit,
            OffsetDateTime cursorTime,
            Long cursorId
    ) {
        StringBuilder jpql = new StringBuilder(
                "SELECT c FROM SupportClaim c JOIN FETCH c.supporter LEFT JOIN FETCH c.reviewer WHERE 1=1 "
        );
        Map<String, Object> params = new HashMap<>();
        if (statusFilter != null) {
            jpql.append("AND c.status = :statusFilter ");
            params.put("statusFilter", statusFilter);
        }
        if (cursorTime != null && cursorId != null) {
            jpql.append("AND (c.createdAt < :cursorTime OR (c.createdAt = :cursorTime AND c.claimId < :cursorId)) ");
            params.put("cursorTime", cursorTime);
            params.put("cursorId", cursorId);
        }
        jpql.append("ORDER BY c.createdAt DESC, c.claimId DESC");

        TypedQuery<SupportClaim> query = em.createQuery(jpql.toString(), SupportClaim.class);
        params.forEach(query::setParameter);
        query.setMaxResults(limit + 1);
        return query.getResultList();
    }

    public Optional<SupportClaim> findSupportClaimById(Long claimId, boolean forUpdate) {
        String jpql = forUpdate
                ? "SELECT c FROM SupportClaim c WHERE c.claimId = :claimId"
                : "SELECT c FROM SupportClaim c JOIN FETCH c.supporter LEFT JOIN FETCH c.reviewer WHERE c.claimId = :claimId";
        TypedQuery<SupportClaim> query = em.createQuery(jpql, SupportClaim.class)
                .setParameter("claimId", claimId);
        if (forUpdate) {
            query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        }
        List<SupportClaim> results = query.getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
