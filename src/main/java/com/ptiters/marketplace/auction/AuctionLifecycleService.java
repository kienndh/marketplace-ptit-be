package com.ptiters.marketplace.auction;

import com.ptiters.marketplace.notification.NotificationType;
import com.ptiters.marketplace.notification.NotificationWriter;
import com.ptiters.marketplace.order.OrderQueryRepository;
import com.ptiters.marketplace.order.PostCommit;
import com.ptiters.marketplace.product.Product;
import com.ptiters.marketplace.realtime.AuctionEventPublisher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

/**
 * Vòng đời phiên đấu giá: SCHEDULED → ACTIVE → ENDED (port {@code close_single_auction_if_expired},
 * {@code activate_or_end_single_scheduled_auction}, {@code timeout_single_winner_checkout}).
 *
 * <p>Mỗi hàm public là MỘT transaction (REQUIRED): gọi từ worker/ngoài transaction thì commit độc lập, gọi từ
 * transaction của caller thì tham gia vào đó. Idempotent: phiên đã ENDED quét lại → no-op, không sinh thông báo trùng.
 * Khoá theo thứ tự cố định: auction → product.
 */
@Service
@Transactional
public class AuctionLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(AuctionLifecycleService.class);

    private final AuctionQueryRepository auctions;
    private final OrderQueryRepository orders;
    private final NotificationWriter notifications;
    private final AuctionEventPublisher auctionEvents;

    @PersistenceContext
    private EntityManager em;

    @Value("${marketplace.auction.winner-checkout-hours:24}")
    private int winnerCheckoutHours;

    public AuctionLifecycleService(AuctionQueryRepository auctions, OrderQueryRepository orders,
                                   NotificationWriter notifications, AuctionEventPublisher auctionEvents) {
        this.auctions = auctions;
        this.orders = orders;
        this.notifications = notifications;
        this.auctionEvents = auctionEvents;
    }

    /**
     * Đóng phiên hết hạn nếu cần. Chỉ khoá hàng khi thực sự có chuyển trạng thái (kiểm tra nhẹ không khoá trước).
     *
     * @return true nếu có chuyển trạng thái (CHANGED), false nếu no-op / không tồn tại
     */
    public boolean closeSingleAuctionIfExpired(Long auctionId, LocalDateTime now) {
        AuctionQueryRepository.AuctionState peek = auctions.peekState(auctionId).orElse(null);
        if (peek == null || !needsTransition(peek, now)) {
            return false;
        }
        Auction auction = auctions.findByIdForUpdate(auctionId).orElse(null);
        if (auction == null) {
            return false;
        }
        boolean transitioned = false;

        if (auction.getStatus() == AuctionStatus.SCHEDULED) {
            if (!auction.getEndsAt().isAfter(now)) {
                endWithoutWinner(auction, now);
                return true;
            }
            if (!auction.getStartsAt().isAfter(now)) {
                auction.setStatus(AuctionStatus.ACTIVE);
                transitioned = true;
            }
        }

        if (auction.getStatus() == AuctionStatus.ACTIVE && !auction.getEndsAt().isAfter(now)) {
            AuctionBid highest = auctions.findHighestBid(auctionId).orElse(null);
            if (highest == null) {
                endWithoutWinner(auction, now);
                return true;
            }
            Product product = em.find(Product.class, auction.getProductId(), LockModeType.PESSIMISTIC_WRITE);
            if (product != null && product.getStock() == 1 && product.getReservedStock() == 0) {
                BigDecimal price = highest.getAmount();
                Long sellerId = product.getSeller().getUserId();
                Long winnerId = highest.getBidderId();
                String title = product.getTitle();
                auction.setStatus(AuctionStatus.ENDED);
                auction.setWinnerId(winnerId);
                auction.setFinalPrice(price);
                auction.setWinnerCheckoutDeadline(now.plusHours(winnerCheckoutHours));
                product.setReservedStock(1);   // giữ hàng cho người thắng
                em.flush();

                String priceText = money(price);
                notifications.create(sellerId, NotificationType.AUCTION_ENDED, "Đấu giá đã kết thúc",
                        title + " đã kết thúc với giá " + priceText + ".", "AUCTION", auctionId);
                notifications.create(winnerId, NotificationType.AUCTION_WON, "Bạn đã thắng đấu giá",
                        "Bạn đã thắng " + title + " với giá " + priceText + ".", "AUCTION", auctionId);
                PostCommit.run("auction-ended", () -> auctionEvents.publishAuctionEnded(auctionId, price, instant(now)));
                log.info("Closed auction {} with winner {}, price {}", auctionId, winnerId, price);
                return true;
            }
            log.error("Cannot reserve stock for winner of auction {} (stock invariant violation).", auctionId);
        }
        return transitioned;
    }

    /** Worker: SCHEDULED đến giờ → ACTIVE, hoặc kết thúc luôn nếu đã quá ends_at. @return true nếu CHANGED. */
    public boolean activateOrEndSingleScheduledAuction(Long auctionId, LocalDateTime now) {
        Auction auction = auctions.findByIdForUpdate(auctionId).orElse(null);
        if (auction == null || auction.getStatus() != AuctionStatus.SCHEDULED) {
            return false;
        }
        if (!auction.getEndsAt().isAfter(now)) {
            endWithoutWinner(auction, now);
            return true;
        }
        if (!auction.getStartsAt().isAfter(now)) {
            auction.setStatus(AuctionStatus.ACTIVE);
            em.flush();
            BigDecimal starting = auction.getStartingPrice();
            PostCommit.run("auction-started", () -> auctionEvents.publishAuctionStarted(auctionId, starting, instant(now)));
            return true;
        }
        return false;
    }

    /**
     * Worker: giải phóng giữ hàng khi người thắng quá hạn checkout mà chưa có đơn.
     * @return true nếu đã trả {@code reserved_stock} về 0 (CHANGED).
     */
    public boolean timeoutSingleWinnerCheckout(Long auctionId, LocalDateTime now) {
        Auction auction = auctions.findByIdForUpdate(auctionId).orElse(null);
        if (auction == null || auction.getStatus() != AuctionStatus.ENDED
                || auction.getWinnerCheckoutDeadline() == null || auction.getWinnerCheckoutDeadline().isAfter(now)) {
            return false;
        }
        if (orders.existsByAuctionId(auctionId)) {
            return false;
        }
        Product product = em.find(Product.class, auction.getProductId(), LockModeType.PESSIMISTIC_WRITE);
        if (product == null || product.getReservedStock() < 1) {
            return false;
        }
        product.setReservedStock(0);
        log.info("Released expired winner reservation on product {} from auction {}", product.getProductId(), auctionId);
        return true;
    }

    // ------------------------------------------------------------------ helpers

    private static boolean needsTransition(AuctionQueryRepository.AuctionState s, LocalDateTime now) {
        return switch (s.status()) {
            case SCHEDULED -> !s.startsAt().isAfter(now) || !s.endsAt().isAfter(now);
            case ACTIVE -> !s.endsAt().isAfter(now);
            default -> false;
        };
    }

    private void endWithoutWinner(Auction auction, LocalDateTime now) {
        Long auctionId = auction.getAuctionId();
        Product product = em.find(Product.class, auction.getProductId());
        Long sellerId = product.getSeller().getUserId();
        String title = product.getTitle();
        auction.setStatus(AuctionStatus.ENDED);
        auction.setWinnerId(null);
        auction.setFinalPrice(null);
        auction.setWinnerCheckoutDeadline(null);
        em.flush();
        notifications.create(sellerId, NotificationType.AUCTION_ENDED, "Đấu giá đã kết thúc",
                "Phiên đấu giá " + title + " đã kết thúc mà chưa có người thắng.", "AUCTION", auctionId);
        PostCommit.run("auction-ended-no-bids", () -> auctionEvents.publishAuctionEnded(auctionId, null, instant(now)));
        log.info("Closed auction {} with no bids", auctionId);
    }

    static Instant instant(LocalDateTime utcNaive) {
        return utcNaive.toInstant(ZoneOffset.UTC);
    }

    /** Python: {@code f"{int(amount):,} đ"} → "90,000 đ". */
    static String money(BigDecimal amount) {
        return String.format(Locale.US, "%,d", amount.toBigInteger()) + " đ";
    }
}
