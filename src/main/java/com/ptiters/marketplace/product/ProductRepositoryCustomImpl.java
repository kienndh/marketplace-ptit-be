package com.ptiters.marketplace.product;

import com.ptiters.marketplace.user.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class ProductRepositoryCustomImpl implements ProductRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    public static String encodeCursor(LocalDateTime createdAt, Long productId) {
        if (createdAt == null || productId == null) {
            return null;
        }
        String payload = createdAt.toString() + "|" + productId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static String encodeInstantCursor(Instant instant, Long productId) {
        if (instant == null || productId == null) {
            return null;
        }
        String payload = instant.toString() + "|" + productId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public PaginatedResult<Product> listPublic(
            Integer categoryId,
            Long sellerId,
            UUID sellerPublicId,
            ListingType listingType,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String searchQuery,
            int limit,
            LocalDateTime cursorTime,
            Long cursorPid
    ) {
        StringBuilder jpql = new StringBuilder(
                "SELECT p FROM Product p " +
                "JOIN FETCH p.seller s " +
                "JOIN FETCH p.category c " +
                "WHERE p.isDeleted = false " +
                "AND p.status = com.ptiters.marketplace.product.ProductStatus.AVAILABLE " +
                "AND p.moderationStatus = com.ptiters.marketplace.product.ProductModerationStatus.CLEAR " +
                "AND p.approvalStatus = com.ptiters.marketplace.product.ProductApprovalStatus.APPROVED " +
                "AND s.status = :sellerActiveStatus "
        );

        Map<String, Object> params = new HashMap<>();
        params.put("sellerActiveStatus", UserStatus.ACTIVE);

        if (categoryId != null) {
            jpql.append("AND p.category.categoryId = :categoryId ");
            params.put("categoryId", categoryId);
        }

        if (sellerPublicId != null) {
            jpql.append("AND s.publicId = :sellerPublicId ");
            params.put("sellerPublicId", sellerPublicId);
        } else if (sellerId != null) {
            jpql.append("AND s.userId = :sellerId ");
            params.put("sellerId", sellerId);
        }

        if (listingType != null) {
            jpql.append("AND p.listingType = :listingType ");
            params.put("listingType", listingType);
        }

        if (minPrice != null) {
            jpql.append("AND p.price >= :minPrice ");
            params.put("minPrice", minPrice);
        }

        if (maxPrice != null) {
            jpql.append("AND p.price <= :maxPrice ");
            params.put("maxPrice", maxPrice);
        }

        if (searchQuery != null && !searchQuery.isBlank()) {
            String kw = "%" + searchQuery.trim().toLowerCase() + "%";
            jpql.append("AND (LOWER(p.title) LIKE :searchKw OR LOWER(p.description) LIKE :searchKw) ");
            params.put("searchKw", kw);
        }

        if (cursorTime != null && cursorPid != null) {
            jpql.append("AND (p.createdAt < :cursorTime OR (p.createdAt = :cursorTime AND p.productId < :cursorPid)) ");
            params.put("cursorTime", cursorTime);
            params.put("cursorPid", cursorPid);
        }

        jpql.append("ORDER BY p.createdAt DESC, p.productId DESC");

        TypedQuery<Product> query = em.createQuery(jpql.toString(), Product.class);
        params.forEach(query::setParameter);
        query.setMaxResults(limit + 1);

        List<Product> rows = query.getResultList();
        boolean hasMore = rows.size() > limit;
        List<Product> items = hasMore ? rows.subList(0, limit) : rows;

        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            Product last = items.get(items.size() - 1);
            nextCursor = encodeCursor(last.getCreatedAt(), last.getProductId());
        }

        return new PaginatedResult<>(items, hasMore, nextCursor);
    }

    @Override
    public PaginatedResult<Product> listBySeller(
            Long sellerId,
            ProductStatus status,
            ProductApprovalStatus approvalStatus,
            int limit,
            LocalDateTime cursorTime,
            Long cursorPid
    ) {
        StringBuilder jpql = new StringBuilder(
                "SELECT p FROM Product p " +
                "JOIN FETCH p.seller s " +
                "JOIN FETCH p.category c " +
                "WHERE s.userId = :sellerId " +
                "AND p.isDeleted = false "
        );

        Map<String, Object> params = new HashMap<>();
        params.put("sellerId", sellerId);

        if (status != null) {
            jpql.append("AND p.status = :status ");
            params.put("status", status);
        }

        if (approvalStatus != null) {
            jpql.append("AND p.approvalStatus = :approvalStatus ");
            params.put("approvalStatus", approvalStatus);
        }

        if (cursorTime != null && cursorPid != null) {
            jpql.append("AND (p.createdAt < :cursorTime OR (p.createdAt = :cursorTime AND p.productId < :cursorPid)) ");
            params.put("cursorTime", cursorTime);
            params.put("cursorPid", cursorPid);
        }

        jpql.append("ORDER BY p.createdAt DESC, p.productId DESC");

        TypedQuery<Product> query = em.createQuery(jpql.toString(), Product.class);
        params.forEach(query::setParameter);
        query.setMaxResults(limit + 1);

        List<Product> rows = query.getResultList();
        boolean hasMore = rows.size() > limit;
        List<Product> items = hasMore ? rows.subList(0, limit) : rows;

        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            Product last = items.get(items.size() - 1);
            nextCursor = encodeCursor(last.getCreatedAt(), last.getProductId());
        }

        return new PaginatedResult<>(items, hasMore, nextCursor);
    }

    @Override
    public PaginatedResult<Product> listAdminQueue(
            ProductApprovalStatus approvalStatus,
            int limit,
            Instant cursorTime,
            Long cursorPid
    ) {
        StringBuilder jpql = new StringBuilder(
                "SELECT p FROM Product p " +
                "JOIN FETCH p.seller s " +
                "JOIN FETCH p.category c " +
                "WHERE p.isDeleted = false " +
                "AND p.approvalStatus = :approvalStatus "
        );

        Map<String, Object> params = new HashMap<>();
        params.put("approvalStatus", approvalStatus != null ? approvalStatus : ProductApprovalStatus.PENDING);

        boolean isPending = (approvalStatus == null || approvalStatus == ProductApprovalStatus.PENDING);

        if (isPending) {
            if (cursorTime != null && cursorPid != null) {
                jpql.append("AND (p.submittedForReviewAt > :cursorTime OR (p.submittedForReviewAt = :cursorTime AND p.productId > :cursorPid)) ");
                params.put("cursorTime", cursorTime);
                params.put("cursorPid", cursorPid);
            }
            jpql.append("ORDER BY p.submittedForReviewAt ASC, p.productId ASC");
        } else {
            if (cursorTime != null && cursorPid != null) {
                jpql.append("AND (p.reviewedAt < :cursorTime OR (p.reviewedAt = :cursorTime AND p.productId < :cursorPid)) ");
                params.put("cursorTime", cursorTime);
                params.put("cursorPid", cursorPid);
            }
            jpql.append("ORDER BY p.reviewedAt DESC, p.productId DESC");
        }

        TypedQuery<Product> query = em.createQuery(jpql.toString(), Product.class);
        params.forEach(query::setParameter);
        query.setMaxResults(limit + 1);

        List<Product> rows = query.getResultList();
        boolean hasMore = rows.size() > limit;
        List<Product> items = hasMore ? rows.subList(0, limit) : rows;

        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            Product last = items.get(items.size() - 1);
            Instant timeKey = isPending ? last.getSubmittedForReviewAt() : last.getReviewedAt();
            if (timeKey == null) {
                timeKey = Instant.now();
            }
            nextCursor = encodeInstantCursor(timeKey, last.getProductId());
        }

        return new PaginatedResult<>(items, hasMore, nextCursor);
    }
}
