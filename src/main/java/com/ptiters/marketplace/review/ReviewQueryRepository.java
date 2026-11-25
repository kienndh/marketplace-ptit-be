package com.ptiters.marketplace.review;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Truy vấn nghiệp vụ review (repository cơ bản của Wave 0 giữ nguyên). */
@Repository
public class ReviewQueryRepository {

    @PersistenceContext
    private EntityManager em;

    public Optional<Review> findByOrderAndReviewer(Long orderId, Long reviewerId, boolean forUpdate) {
        var q = em.createQuery(
                "select r from Review r where r.orderId = :orderId and r.reviewerId = :reviewerId", Review.class)
                .setParameter("orderId", orderId)
                .setParameter("reviewerId", reviewerId);
        if (forUpdate) {
            q.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        }
        return q.getResultStream().findFirst();
    }

    public Optional<Review> findByOrderId(Long orderId) {
        return em.createQuery("select r from Review r where r.orderId = :orderId order by r.reviewId asc", Review.class)
                .setParameter("orderId", orderId)
                .setMaxResults(1)
                .getResultStream().findFirst();
    }

    /** Keyset: created_at DESC, review_id DESC; lấy {@code fetchSize} bản ghi (limit + 1). */
    public List<Review> findSellerReviews(Long sellerId, int fetchSize, LocalDateTime cursorAt, Long cursorId) {
        StringBuilder hql = new StringBuilder("select r from Review r where r.revieweeId = :sellerId");
        boolean cursor = cursorAt != null && cursorId != null;
        if (cursor) {
            hql.append(" and (r.createdAt < :cAt or (r.createdAt = :cAt and r.reviewId < :cId))");
        }
        hql.append(" order by r.createdAt desc, r.reviewId desc");
        var q = em.createQuery(hql.toString(), Review.class).setParameter("sellerId", sellerId);
        if (cursor) {
            q.setParameter("cAt", cursorAt).setParameter("cId", cursorId);
        }
        return q.setMaxResults(fetchSize).getResultList();
    }
}
