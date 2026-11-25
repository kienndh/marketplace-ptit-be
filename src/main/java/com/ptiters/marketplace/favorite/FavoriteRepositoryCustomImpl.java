package com.ptiters.marketplace.favorite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class FavoriteRepositoryCustomImpl implements FavoriteRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    public static String encodeCursor(LocalDateTime createdAt, Long productId) {
        if (createdAt == null || productId == null) {
            return null;
        }
        String payload = createdAt.toString() + "|" + productId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public PaginatedResult<Favorite> listFavorites(
            Long userId,
            int limit,
            LocalDateTime cursorTime,
            Long cursorPid
    ) {
        StringBuilder jpql = new StringBuilder(
                "SELECT f FROM Favorite f " +
                "JOIN FETCH f.product p " +
                "JOIN FETCH p.seller s " +
                "JOIN FETCH p.category c " +
                "WHERE f.userId = :userId " +
                "AND p.isDeleted = false " +
                "AND p.status = com.ptiters.marketplace.product.ProductStatus.AVAILABLE " +
                "AND p.approvalStatus = com.ptiters.marketplace.product.ProductApprovalStatus.APPROVED " +
                "AND p.moderationStatus = com.ptiters.marketplace.product.ProductModerationStatus.CLEAR "
        );

        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);

        if (cursorTime != null && cursorPid != null) {
            jpql.append("AND (f.createdAt < :cursorTime OR (f.createdAt = :cursorTime AND f.productId < :cursorPid)) ");
            params.put("cursorTime", cursorTime);
            params.put("cursorPid", cursorPid);
        }

        jpql.append("ORDER BY f.createdAt DESC, f.productId DESC");

        TypedQuery<Favorite> query = em.createQuery(jpql.toString(), Favorite.class);
        params.forEach(query::setParameter);
        query.setMaxResults(limit + 1);

        List<Favorite> rows = query.getResultList();
        boolean hasMore = rows.size() > limit;
        List<Favorite> items = hasMore ? rows.subList(0, limit) : rows;

        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            Favorite last = items.get(items.size() - 1);
            nextCursor = encodeCursor(last.getCreatedAt(), last.getProductId());
        }

        return new PaginatedResult<>(items, hasMore, nextCursor);
    }
}
