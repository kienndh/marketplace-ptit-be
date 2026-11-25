package com.ptiters.marketplace.order;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Truy vấn nghiệp vụ cho Order (tách khỏi {@link OrderRepository} Wave 0). Port từ {@code orders/repository.py}.
 * Mọi tham số (kể cả enum) đều qua {@code setParameter}, không inline literal vào HQL.
 */
@Repository
public class OrderQueryRepository {

    @PersistenceContext
    private EntityManager em;

    public Optional<Order> findById(Long orderId) {
        return Optional.ofNullable(em.find(Order.class, orderId));
    }

    /** {@code SELECT ... FOR UPDATE} trên hàng đơn hàng (Python: {@code get_order_by_id(for_update=True)}). */
    public Optional<Order> findByIdForUpdate(Long orderId) {
        return Optional.ofNullable(em.find(Order.class, orderId, LockModeType.PESSIMISTIC_WRITE));
    }

    public boolean existsByOrderCode(String orderCode) {
        Long count = em.createQuery("select count(o) from Order o where o.orderCode = :code", Long.class)
                .setParameter("code", orderCode)
                .getSingleResult();
        return count != null && count > 0;
    }

    public boolean existsByAuctionId(Long auctionId) {
        Long count = em.createQuery("select count(o) from Order o where o.auctionId = :aid", Long.class)
                .setParameter("aid", auctionId)
                .getSingleResult();
        return count != null && count > 0;
    }

    public Optional<Order> findLatestByAuctionId(Long auctionId) {
        return em.createQuery("select o from Order o where o.auctionId = :aid order by o.orderId desc", Order.class)
                .setParameter("aid", auctionId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    /** Keyset {@code (created_at, order_id) DESC}, lấy limit+1 để biết còn trang sau. */
    public List<Order> listByBuyer(Long buyerId, OrderStatus status, LocalDateTime cursorCreatedAt, Long cursorOrderId,
                                   int limit) {
        return list("buyerId", buyerId, status, cursorCreatedAt, cursorOrderId, limit);
    }

    public List<Order> listBySeller(Long sellerId, OrderStatus status, LocalDateTime cursorCreatedAt, Long cursorOrderId,
                                    int limit) {
        return list("sellerId", sellerId, status, cursorCreatedAt, cursorOrderId, limit);
    }

    private List<Order> list(String ownerField, Long ownerId, OrderStatus status, LocalDateTime cursorCreatedAt,
                             Long cursorOrderId, int limit) {
        StringBuilder hql = new StringBuilder("select o from Order o where o.").append(ownerField).append(" = :owner");
        if (status != null) {
            hql.append(" and o.status = :status");
        }
        if (cursorCreatedAt != null && cursorOrderId != null) {
            hql.append(" and (o.createdAt < :cAt or (o.createdAt = :cAt and o.orderId < :cId))");
        }
        hql.append(" order by o.createdAt desc, o.orderId desc");
        TypedQuery<Order> q = em.createQuery(hql.toString(), Order.class).setParameter("owner", ownerId);
        if (status != null) {
            q.setParameter("status", status);
        }
        if (cursorCreatedAt != null && cursorOrderId != null) {
            q.setParameter("cAt", cursorCreatedAt).setParameter("cId", cursorOrderId);
        }
        return q.setMaxResults(limit + 1).getResultList();
    }

    /** Nạp một lượt các OrderItem của nhiều đơn (tránh N+1 khi dựng danh sách). */
    public List<OrderItem> findItemsByOrderIds(Collection<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery(
                        "select i from OrderItem i where i.order.orderId in :ids order by i.orderItemId asc",
                        OrderItem.class)
                .setParameter("ids", orderIds)
                .getResultList();
    }

    /** Python: {@code get_expired_pending_order_ids}. */
    public List<Long> findExpiredPendingOrderIds(LocalDateTime nowUtc, int limit) {
        return em.createQuery(
                        "select o.orderId from Order o where o.status = :status and o.autoCancelAt is not null "
                                + "and o.autoCancelAt <= :now order by o.orderId asc", Long.class)
                .setParameter("status", OrderStatus.PENDING)
                .setParameter("now", nowUtc)
                .setMaxResults(limit)
                .getResultList();
    }
}
