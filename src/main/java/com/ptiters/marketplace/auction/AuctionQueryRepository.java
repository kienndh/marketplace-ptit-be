package com.ptiters.marketplace.auction;

import com.ptiters.marketplace.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Truy vấn nghiệp vụ cho Auction/AuctionBid (port {@code auctions/repository.py}). Tách khỏi {@link AuctionRepository} Wave 0.
 * Tham số (kể cả enum) luôn qua {@code setParameter}.
 */
@Repository
public class AuctionQueryRepository {

    private static final List<AuctionStatus> OPEN = List.of(AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE);

    /** Ảnh chụp nhẹ (không khoá) dùng để quyết định có cần khoá hàng auction hay không. */
    public record AuctionState(AuctionStatus status, LocalDateTime startsAt, LocalDateTime endsAt) {
    }

    @PersistenceContext
    private EntityManager em;

    public Optional<Auction> findById(Long auctionId) {
        return Optional.ofNullable(em.find(Auction.class, auctionId));
    }

    /** {@code SELECT ... FOR UPDATE} trên hàng auction (đặt giá / đóng phiên / checkout chạy tuần tự). */
    public Optional<Auction> findByIdForUpdate(Long auctionId) {
        return Optional.ofNullable(em.find(Auction.class, auctionId, LockModeType.PESSIMISTIC_WRITE));
    }

    /** Đọc scalar không khoá, không đưa entity vào persistence context. */
    public Optional<AuctionState> peekState(Long auctionId) {
        return em.createQuery("select a.status, a.startsAt, a.endsAt from Auction a where a.auctionId = :id", Object[].class)
                .setParameter("id", auctionId)
                .getResultStream()
                .findFirst()
                .map(r -> new AuctionState((AuctionStatus) r[0], (LocalDateTime) r[1], (LocalDateTime) r[2]));
    }

    public Optional<Auction> findOpenByProductId(Long productId) {
        return em.createQuery("select a from Auction a where a.productId = :pid and a.status in :open", Auction.class)
                .setParameter("pid", productId)
                .setParameter("open", OPEN)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    /** Vòng SCHEDULED/ACTIVE hiện tại; nếu không có → vòng mới nhất theo created_at DESC. */
    public Optional<Auction> findOpenOrLatestByProductId(Long productId) {
        Optional<Auction> open = em.createQuery(
                        "select a from Auction a where a.productId = :pid and a.status in :open order by a.auctionId desc",
                        Auction.class)
                .setParameter("pid", productId)
                .setParameter("open", OPEN)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
        if (open.isPresent()) {
            return open;
        }
        return em.createQuery(
                        "select a from Auction a where a.productId = :pid order by a.createdAt desc, a.auctionId desc",
                        Auction.class)
                .setParameter("pid", productId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    public long countBids(Long auctionId) {
        return em.createQuery("select count(b) from AuctionBid b where b.auction.auctionId = :id", Long.class)
                .setParameter("id", auctionId)
                .getSingleResult();
    }

    /** Bid cao nhất: amount DESC, created_at ASC, bid_id ASC. */
    public Optional<AuctionBid> findHighestBid(Long auctionId) {
        return em.createQuery(
                        "select b from AuctionBid b where b.auction.auctionId = :id "
                                + "order by b.amount desc, b.createdAt asc, b.bidId asc", AuctionBid.class)
                .setParameter("id", auctionId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    /** Keyset (amount DESC, created_at ASC, bid_id ASC); lấy limit+1. */
    public List<AuctionBid> listBidsKeyset(Long auctionId, BigDecimal cAmount, LocalDateTime cCreatedAt, Long cBidId,
                                           int limit) {
        boolean hasCursor = cAmount != null && cCreatedAt != null && cBidId != null;
        String hql = "select b from AuctionBid b where b.auction.auctionId = :id"
                + (hasCursor ? " and (b.amount < :cAmount or (b.amount = :cAmount and "
                + "(b.createdAt > :cAt or (b.createdAt = :cAt and b.bidId > :cId))))" : "")
                + " order by b.amount desc, b.createdAt asc, b.bidId asc";
        var q = em.createQuery(hql, AuctionBid.class).setParameter("id", auctionId);
        if (hasCursor) {
            q.setParameter("cAmount", cAmount).setParameter("cAt", cCreatedAt).setParameter("cId", cBidId);
        }
        return q.setMaxResults(limit + 1).getResultList();
    }

    public List<User> findUsersByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return em.createQuery("select u from User u where u.userId in :ids", User.class)
                .setParameter("ids", ids)
                .getResultList();
    }

    // ---------------------------------------------------------------- worker sweeps

    public List<Long> findScheduledToSweep(LocalDateTime now, int limit) {
        return em.createQuery(
                        "select a.auctionId from Auction a where a.status = :st and a.startsAt <= :now "
                                + "order by a.startsAt asc, a.auctionId asc", Long.class)
                .setParameter("st", AuctionStatus.SCHEDULED)
                .setParameter("now", now)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<Long> findActiveToClose(LocalDateTime now, int limit) {
        return em.createQuery(
                        "select a.auctionId from Auction a where a.status = :st and a.endsAt <= :now "
                                + "order by a.endsAt asc, a.auctionId asc", Long.class)
                .setParameter("st", AuctionStatus.ACTIVE)
                .setParameter("now", now)
                .setMaxResults(limit)
                .getResultList();
    }

    /** ENDED có winner, quá hạn checkout, sản phẩm còn giữ hàng, chưa có đơn. */
    public List<Long> findExpiredWinnerAuctions(LocalDateTime now, int limit) {
        return em.createQuery(
                        "select a.auctionId from Auction a, Product p where p.productId = a.productId "
                                + "and a.status = :st and a.winnerId is not null and a.winnerCheckoutDeadline is not null "
                                + "and a.winnerCheckoutDeadline <= :now and p.reservedStock > 0 "
                                + "and not exists (select o.orderId from Order o where o.auctionId = a.auctionId) "
                                + "order by a.winnerCheckoutDeadline asc, a.auctionId asc", Long.class)
                .setParameter("st", AuctionStatus.ENDED)
                .setParameter("now", now)
                .setMaxResults(limit)
                .getResultList();
    }

    /** Phiên thắng chờ checkout của user (còn hạn, chưa có đơn, sản phẩm chưa xoá). */
    public List<Auction> findWonPendingCheckout(Long userId, LocalDateTime now) {
        return em.createQuery(
                        "select a from Auction a, Product p where p.productId = a.productId "
                                + "and a.status = :st and a.winnerId = :uid and a.finalPrice is not null "
                                + "and a.winnerCheckoutDeadline is not null and a.winnerCheckoutDeadline >= :now "
                                + "and p.isDeleted = false "
                                + "and not exists (select o.orderId from Order o where o.auctionId = a.auctionId) "
                                + "order by a.winnerCheckoutDeadline asc, a.auctionId desc", Auction.class)
                .setParameter("st", AuctionStatus.ENDED)
                .setParameter("uid", userId)
                .setParameter("now", now)
                .getResultList();
    }
}
