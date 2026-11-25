package com.ptiters.marketplace.auction;

import com.ptiters.marketplace.exception.ApiException;
import com.ptiters.marketplace.notification.NotificationType;
import com.ptiters.marketplace.notification.NotificationWriter;
import com.ptiters.marketplace.order.CapabilityRestrictedException;
import com.ptiters.marketplace.order.DateTimes;
import com.ptiters.marketplace.order.Order;
import com.ptiters.marketplace.order.OrderDetailResponse;
import com.ptiters.marketplace.order.OrderQueryRepository;
import com.ptiters.marketplace.order.OrderService;
import com.ptiters.marketplace.order.PostCommit;
import com.ptiters.marketplace.product.ListingType;
import com.ptiters.marketplace.product.Product;
import com.ptiters.marketplace.product.ProductApprovalStatus;
import com.ptiters.marketplace.product.ProductImage;
import com.ptiters.marketplace.product.ProductModerationStatus;
import com.ptiters.marketplace.product.ProductStatus;
import com.ptiters.marketplace.realtime.AuctionEventPublisher;
import com.ptiters.marketplace.realtime.UserEventPublisher;
import com.ptiters.marketplace.user.CapabilityService;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserRole;
import com.ptiters.marketplace.user.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Nghiệp vụ đấu giá (port {@code app/modules/auctions/service.py}, phần REST).
 *
 * <p>Đặt giá khoá hàng {@code auctions} bằng {@code SELECT ... FOR UPDATE}: các bid cùng phiên chạy tuần tự, bid hợp lệ
 * luôn cao hơn bid trước ít nhất {@code bid_step}. Realtime/notification chỉ phát SAU commit.
 */
@Service
public class AuctionService {

    private static final Logger log = LoggerFactory.getLogger(AuctionService.class);
    private static final String AUCTION_NOT_FOUND = "Không tìm thấy phiên đấu giá.";
    private static final Pattern C_AMOUNT = Pattern.compile("\"amount\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern C_CREATED = Pattern.compile("\"created_at\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern C_BID = Pattern.compile("\"bid_id\"\\s*:\\s*(\\d+)");

    private final AuctionQueryRepository auctions;
    private final OrderQueryRepository orders;
    private final AuctionLifecycleService lifecycle;
    private final OrderService orderService;
    private final NotificationWriter notifications;
    private final AuctionEventPublisher auctionEvents;
    private final UserEventPublisher userEvents;
    private final CapabilityService capabilityService;
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;

    @PersistenceContext
    private EntityManager em;

    public AuctionService(AuctionQueryRepository auctions, OrderQueryRepository orders,
                          AuctionLifecycleService lifecycle, OrderService orderService, NotificationWriter notifications,
                          AuctionEventPublisher auctionEvents, UserEventPublisher userEvents,
                          CapabilityService capabilityService, PlatformTransactionManager txManager) {
        this.auctions = auctions;
        this.orders = orders;
        this.lifecycle = lifecycle;
        this.orderService = orderService;
        this.notifications = notifications;
        this.auctionEvents = auctionEvents;
        this.userEvents = userEvents;
        this.capabilityService = capabilityService;
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(txManager);
    }

    // ================================================================== seller: enable / cancel / convert

    @Transactional
    public AuctionDetailResponse enableAuction(User seller, AuctionCreateRequest p) {
        if (seller.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("Tài khoản của bạn không được phép tạo phiên đấu giá.");
        }
        if (!capabilityService.getUserAccessStatus(seller).isCanSell()) {
            throw CapabilityRestrictedException.sell();
        }
        LocalDateTime now = DateTimes.nowUtc();

        Product product = em.find(Product.class, p.productId(), LockModeType.PESSIMISTIC_WRITE);
        if (product == null || Boolean.TRUE.equals(product.getIsDeleted())
                || !product.getSeller().getUserId().equals(seller.getUserId())) {
            throw ApiException.notFound("Sản phẩm không tồn tại hoặc bạn không có quyền sở hữu.");
        }
        if (product.getStatus() != ProductStatus.AVAILABLE) {
            throw ApiException.badRequest("Chỉ có thể bật đấu giá cho sản phẩm đang ở trạng thái AVAILABLE.");
        }
        if (product.getApprovalStatus() != ProductApprovalStatus.APPROVED) {
            throw ApiException.badRequest("Sản phẩm chưa được quản trị viên duyệt để bán.");
        }
        if (product.getModerationStatus() == ProductModerationStatus.BLOCKED) {
            throw ApiException.badRequest("Không thể tạo đấu giá cho sản phẩm đang bị quản trị viên ẩn.");
        }
        if (product.getStock() != 1 || product.getReservedStock() != 0) {
            throw ApiException.badRequest("Sản phẩm đấu giá phải có đúng 1 tồn kho khả dụng và chưa bị giữ hàng.");
        }
        if (p.bidStep().signum() <= 0) {
            throw ApiException.badRequest("Bước giá phải lớn hơn 0.");
        }

        LocalDateTime startsAt;
        LocalDateTime endsAt;
        AuctionStatus status;
        if (p.durationMinutes() != null) {
            if (p.durationMinutes() <= 0) {
                throw ApiException.badRequest("Thời lượng đấu giá phải lớn hơn 0 phút.");
            }
            startsAt = now;
            endsAt = now.plusMinutes(p.durationMinutes());
            status = AuctionStatus.ACTIVE;
        } else {
            startsAt = DateTimes.parseToUtcNaive(p.startsAt(), "starts_at");
            endsAt = DateTimes.parseToUtcNaive(p.endsAt(), "ends_at");
            if (startsAt == null) {
                startsAt = now;
            } else if (startsAt.isBefore(now)) {
                throw ApiException.badRequest("Thời gian bắt đầu không được ở trong quá khứ.");
            }
            if (endsAt == null) {
                throw ApiException.badRequest("Cần cung cấp duration_minutes hoặc ends_at.");
            }
            if (!endsAt.isAfter(startsAt)) {
                throw ApiException.badRequest("Thời gian kết thúc phải diễn ra sau thời gian bắt đầu.");
            }
            status = !startsAt.isAfter(now) ? AuctionStatus.ACTIVE : AuctionStatus.SCHEDULED;
        }

        if (auctions.findOpenByProductId(product.getProductId()).isPresent()) {
            throw ApiException.badRequest("Sản phẩm đã có một phiên đấu giá đang hoạt động hoặc được lên lịch.");
        }

        Auction auction = Auction.builder()
                .productId(product.getProductId())
                .startingPrice(product.getPrice())
                .bidStep(p.bidStep())
                .startsAt(startsAt)
                .endsAt(endsAt)
                .status(status)
                .build();
        em.persist(auction);
        product.setListingType(ListingType.AUCTION);
        em.flush();

        if (status == AuctionStatus.ACTIVE) {
            Long id = auction.getAuctionId();
            BigDecimal starting = auction.getStartingPrice();
            PostCommit.run("auction-started", () -> auctionEvents.publishAuctionStarted(id, starting,
                    AuctionLifecycleService.instant(now)));
        }
        return toDetail(auction, seller, now);
    }

    @Transactional
    public AuctionDetailResponse cancelAuction(User seller, Long auctionId) {
        LocalDateTime now = DateTimes.nowUtc();
        Auction auction = auctions.findByIdForUpdate(auctionId).orElseThrow(() -> ApiException.notFound(AUCTION_NOT_FOUND));
        Product product = em.find(Product.class, auction.getProductId(), LockModeType.PESSIMISTIC_WRITE);
        if (product == null || (!product.getSeller().getUserId().equals(seller.getUserId())
                && seller.getRole() != UserRole.ADMIN)) {
            throw ApiException.forbidden("Bạn không có quyền hủy phiên đấu giá này.");
        }
        if (auction.getStatus() != AuctionStatus.SCHEDULED && auction.getStatus() != AuctionStatus.ACTIVE) {
            throw ApiException.badRequest("Chỉ có thể hủy phiên đấu giá đang được lên lịch hoặc đang hoạt động.");
        }
        if (auctions.countBids(auctionId) > 0) {
            throw ApiException.badRequest("Không thể hủy phiên đấu giá đã có lượt đặt giá.");
        }
        auction.setStatus(AuctionStatus.CANCELLED);
        product.setListingType(ListingType.FIXED_PRICE);
        em.flush();
        PostCommit.run("auction-cancelled", () -> auctionEvents.publishAuctionCancelled(auctionId,
                AuctionLifecycleService.instant(now)));
        return toDetail(auction, seller, now);
    }

    @Transactional
    public void convertProductToFixedPrice(User seller, Long productId) {
        Product product = em.find(Product.class, productId, LockModeType.PESSIMISTIC_WRITE);
        if (product == null || Boolean.TRUE.equals(product.getIsDeleted())
                || (!product.getSeller().getUserId().equals(seller.getUserId()) && seller.getRole() != UserRole.ADMIN)) {
            throw ApiException.notFound("Sản phẩm không tồn tại hoặc bạn không có quyền sở hữu.");
        }
        if (auctions.findOpenByProductId(productId).isPresent()) {
            throw ApiException.badRequest("Không thể chuyển sang bán giá cố định khi đang có phiên đấu giá hoạt động.");
        }
        if (product.getReservedStock() > 0) {
            throw ApiException.badRequest("Không thể chuyển hình thức bán khi sản phẩm đang có giao dịch giữ hàng.");
        }
        product.setListingType(ListingType.FIXED_PRICE);
    }

    // ================================================================== bidder: place bid

    @Transactional
    public AuctionBidResponse placeBid(User bidder, Long auctionId, PlaceBidRequest p) {
        if (bidder.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("Tài khoản của bạn không được phép đặt giá.");
        }
        if (p.amount().signum() <= 0) {
            throw ApiException.badRequest("Giá đặt phải lớn hơn 0.");
        }
        if (!capabilityService.getUserAccessStatus(bidder).isCanBuy()) {
            throw CapabilityRestrictedException.buy();
        }
        LocalDateTime now = DateTimes.nowUtc();

        // 1. Khoá hàng auction: các bid cùng phiên chạy tuần tự
        Auction auction = auctions.findByIdForUpdate(auctionId).orElseThrow(() -> ApiException.notFound(AUCTION_NOT_FOUND));

        if (auction.getStatus() == AuctionStatus.SCHEDULED && !auction.getStartsAt().isAfter(now)
                && now.isBefore(auction.getEndsAt())) {
            auction.setStatus(AuctionStatus.ACTIVE);   // catch-up
        }
        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            throw ApiException.badRequest("Phiên đấu giá chưa bắt đầu hoặc đã kết thúc.");
        }

        // Đọc lại sản phẩm để chặn race với PRODUCT_HIDE đồng thời
        Product product = em.find(Product.class, auction.getProductId());
        if (product == null || Boolean.TRUE.equals(product.getIsDeleted())
                || product.getModerationStatus() == ProductModerationStatus.BLOCKED
                || product.getApprovalStatus() != ProductApprovalStatus.APPROVED) {
            throw ApiException.badRequest("Sản phẩm đấu giá đã bị ẩn hoặc không còn khả dụng.");
        }
        if (!now.isBefore(auction.getEndsAt())) {
            throw ApiException.badRequest("Phiên đấu giá đã hết thời gian đặt giá.");
        }
        Long sellerId = product.getSeller().getUserId();
        if (sellerId.equals(bidder.getUserId())) {
            throw ApiException.badRequest("Người bán không được tự đặt giá sản phẩm của chính mình.");
        }
        User seller = em.find(User.class, sellerId);
        if (seller == null || seller.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.badRequest("Người bán hiện đang bị khóa tài khoản hoặc không hoạt động.");
        }

        // 2. Snapshot bid cao nhất TRƯỚC khi chèn bid mới
        AuctionBid previous = auctions.findHighestBid(auctionId).orElse(null);
        Long previousBidderId = previous == null ? null : previous.getBidderId();
        BigDecimal minRequired = previous != null
                ? previous.getAmount().add(auction.getBidStep())
                : auction.getStartingPrice().max(BigDecimal.ONE);
        if (p.amount().compareTo(minRequired) < 0) {
            throw ApiException.badRequest("Giá đặt phải tối thiểu là " + AuctionLifecycleService.money(minRequired) + ".");
        }

        // 3. Chèn bid
        AuctionBid bid = AuctionBid.builder()
                .auction(auction)
                .bidderId(bidder.getUserId())
                .amount(p.amount())
                .createdAt(now)
                .build();
        em.persist(bid);
        em.flush();
        long bidCount = auctions.countBids(auctionId);

        // 4. Thông báo (ghi cùng transaction; realtime/push sau commit do NotificationWriter lo)
        String title = product.getTitle();
        String amountText = AuctionLifecycleService.money(bid.getAmount());
        notifications.create(sellerId, NotificationType.AUCTION_BID, "Có lượt đấu giá mới",
                bidder.getFullName() + " vừa đặt giá " + amountText + " cho " + title + ".", "AUCTION", auctionId);
        if (previousBidderId != null && !previousBidderId.equals(bidder.getUserId())) {
            notifications.create(previousBidderId, NotificationType.AUCTION_OUTBID, "Bạn đã bị vượt giá",
                    "Mức giá cao nhất mới của " + title + " là " + amountText + ".", "AUCTION", auctionId);
        }

        BigDecimal amount = bid.getAmount();
        BigDecimal step = auction.getBidStep();
        PostCommit.run("bid-placed", () -> auctionEvents.publishBidPlaced(auctionId, amount, step, bidCount,
                AuctionLifecycleService.instant(now)));

        return new AuctionBidResponse(bid.getBidId(), auctionId, AuctionNames.anonymize(bidder.getFullName()),
                null, bid.getAmount(), bid.getCreatedAt(), true, false);
    }

    // ================================================================== public reads

    /** GET chi tiết: đóng phiên "tại chỗ" nếu đã hết hạn (có thể ghi) nên KHÔNG readOnly. */
    @Transactional
    public AuctionDetailResponse getAuctionDetail(Long auctionId, User currentUser) {
        LocalDateTime now = DateTimes.nowUtc();
        lifecycle.closeSingleAuctionIfExpired(auctionId, now);
        Auction auction = auctions.findById(auctionId).orElseThrow(() -> ApiException.notFound(AUCTION_NOT_FOUND));
        return toDetail(auction, currentUser, now);
    }

    @Transactional
    public AuctionDetailResponse getAuctionByProduct(Long productId, User currentUser) {
        LocalDateTime now = DateTimes.nowUtc();
        Auction auction = auctions.findOpenOrLatestByProductId(productId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên đấu giá nào cho sản phẩm này."));
        if (auction.getStatus() == AuctionStatus.ACTIVE && !auction.getEndsAt().isAfter(now)) {
            lifecycle.closeSingleAuctionIfExpired(auction.getAuctionId(), now);
        }
        return toDetail(auction, currentUser, now);
    }

    @Transactional(readOnly = true)
    public AuctionBidCursorPage listBids(Long auctionId, String cursor, int limit, User currentUser) {
        int lim = Math.max(1, Math.min(limit, 50));
        BigDecimal cAmount = null;
        LocalDateTime cAt = null;
        Long cId = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                String json = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                Matcher a = C_AMOUNT.matcher(json);
                Matcher c = C_CREATED.matcher(json);
                Matcher b = C_BID.matcher(json);
                if (!a.find() || !c.find() || !b.find()) {
                    throw new IllegalArgumentException("bad cursor");
                }
                cAmount = new BigDecimal(a.group(1));
                cAt = LocalDateTime.parse(c.group(1));
                cId = Long.parseLong(b.group(1));
            } catch (RuntimeException e) {
                throw ApiException.badRequest("Invalid cursor format");
            }
        }
        List<AuctionBid> rows = auctions.listBidsKeyset(auctionId, cAmount, cAt, cId, lim);
        boolean hasMore = rows.size() > lim;
        List<AuctionBid> page = hasMore ? rows.subList(0, lim) : rows;

        String next = null;
        if (hasMore && !page.isEmpty()) {
            AuctionBid last = page.get(page.size() - 1);
            String json = "{\"amount\": \"" + last.getAmount().toPlainString() + "\", \"created_at\": \""
                    + last.getCreatedAt() + "\", \"bid_id\": " + last.getBidId() + "}";
            next = Base64.getUrlEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        }
        Long highestBidId = auctions.findHighestBid(auctionId).map(AuctionBid::getBidId).orElse(null);
        Map<Long, User> bidders = auctions.findUsersByIds(
                        page.stream().map(AuctionBid::getBidderId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getUserId, Function.identity()));
        List<AuctionBidResponse> items = page.stream().map(b -> {
            User u = bidders.get(b.getBidderId());
            return new AuctionBidResponse(b.getBidId(), auctionId,
                    AuctionNames.anonymize(u == null ? null : u.getFullName()),
                    u == null ? null : u.getAvatarUrl(), b.getAmount(), b.getCreatedAt(),
                    currentUser != null && b.getBidderId().equals(currentUser.getUserId()),
                    highestBidId != null && b.getBidId().equals(highestBidId));
        }).toList();
        return new AuctionBidCursorPage(items, next, hasMore);
    }

    // ================================================================== winner

    /** Phiên thắng đang chờ checkout của người dùng. Đóng các phiên vừa hết hạn trước (mỗi phiên một transaction). */
    public List<WonAuctionItemResponse> getMyWonAuctions(User currentUser) {
        LocalDateTime now = DateTimes.nowUtc();
        List<Long> candidates = readTx.execute(s -> auctions.findActiveToClose(now, 50));
        for (Long id : candidates == null ? List.<Long>of() : candidates) {
            try {
                lifecycle.closeSingleAuctionIfExpired(id, now);
            } catch (RuntimeException e) {
                log.warn("Error finalizing candidate auction {}: {}", id, e.toString());
            }
        }
        return readTx.execute(s -> auctions.findWonPendingCheckout(currentUser.getUserId(), now).stream()
                .map(a -> {
                    Product p = em.find(Product.class, a.getProductId());
                    if (p == null || Boolean.TRUE.equals(p.getIsDeleted())) {
                        return null;
                    }
                    return new WonAuctionItemResponse(a.getAuctionId(), a.getProductId(), p.getTitle(),
                            thumbnail(p), a.getFinalPrice() == null ? BigDecimal.ZERO : a.getFinalPrice(),
                            a.getWinnerCheckoutDeadline() == null ? now : a.getWinnerCheckoutDeadline(), now);
                })
                .filter(java.util.Objects::nonNull)
                .toList());
    }

    /**
     * Người thắng checkout → tạo Order (tái sử dụng reservation, {@code orders.auction_id} unique).
     * Bước đóng phiên hết hạn chạy ở transaction riêng (commit độc lập, giống Python) rồi checkout trong transaction mới.
     */
    public OrderDetailResponse winnerCheckout(User winner, Long auctionId, WinnerCheckoutRequest p) {
        if (winner.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("Tài khoản của bạn không được phép mua hàng.");
        }
        LocalDateTime now = DateTimes.nowUtc();
        LocalDateTime meetupTime = DateTimes.parseToUtcNaive(p.meetupTime(), "meetup_time");

        Boolean canBuy = readTx.execute(s -> capabilityService.getUserAccessStatus(winner).isCanBuy());
        if (!Boolean.TRUE.equals(canBuy)) {
            throw CapabilityRestrictedException.buy();
        }
        Optional<AuctionQueryRepository.AuctionState> exists = readTx.execute(s -> auctions.peekState(auctionId));
        if (exists == null || exists.isEmpty()) {
            throw ApiException.notFound(AUCTION_NOT_FOUND);
        }
        lifecycle.closeSingleAuctionIfExpired(auctionId, now);

        return writeTx.execute(status -> {
            Auction auction = auctions.findByIdForUpdate(auctionId)
                    .orElseThrow(() -> ApiException.notFound(AUCTION_NOT_FOUND));
            if (auction.getStatus() != AuctionStatus.ENDED) {
                throw ApiException.badRequest("Phiên đấu giá chưa kết thúc.");
            }
            if (auction.getWinnerId() == null || !auction.getWinnerId().equals(winner.getUserId())) {
                throw ApiException.forbidden("Bạn không phải người thắng phiên đấu giá này.");
            }
            if (auction.getFinalPrice() == null || auction.getFinalPrice().signum() <= 0) {
                throw ApiException.badRequest("Giá chốt phiên đấu giá không hợp lệ.");
            }
            if (auction.getWinnerCheckoutDeadline() == null || now.isAfter(auction.getWinnerCheckoutDeadline())) {
                throw ApiException.badRequest("Đã hết thời hạn hoàn tất đặt mua cho phiên đấu giá này.");
            }
            Product product = em.find(Product.class, auction.getProductId(), LockModeType.PESSIMISTIC_WRITE);
            if (product == null || Boolean.TRUE.equals(product.getIsDeleted())) {
                throw ApiException.notFound("Sản phẩm không tồn tại.");
            }
            if (product.getModerationStatus() == ProductModerationStatus.BLOCKED
                    || product.getApprovalStatus() != ProductApprovalStatus.APPROVED) {
                throw ApiException.badRequest("Sản phẩm đã bị quản trị viên ẩn hoặc không còn khả dụng.");
            }
            if (orders.existsByAuctionId(auctionId)) {
                throw ApiException.badRequest("Quyền mua từ phiên đấu giá này đã được sử dụng.");
            }
            if (product.getReservedStock() < 1) {
                throw ApiException.badRequest("Lỗi dữ liệu tồn kho: quyền giữ hàng của người thắng không khả dụng.");
            }

            Order order = orderService.createAuctionWinnerOrder(winner, auctionId, product, auction.getFinalPrice(),
                    new OrderService.AuctionOrderTerms(p.paymentMethod(), p.fulfillmentMethod(), p.meetupPlace(),
                            meetupTime, p.shippingAddress(), p.contactName(), p.contactPhone(), p.buyerNote()),
                    now);

            Long sellerId = order.getSellerId();
            Long orderId = order.getOrderId();
            PostCommit.run("winner-checked-out", () -> {
                userEvents.publishAuctionWinnerCheckedOut(sellerId, auctionId, orderId);
                userEvents.publishOrderCreated(sellerId, orderId);
            });
            return orderService.getOrderDetail(orderId, winner);
        });
    }

    // ================================================================== mapping

    private AuctionDetailResponse toDetail(Auction auction, User currentUser, LocalDateTime now) {
        Long id = auction.getAuctionId();
        long bidCount = auctions.countBids(id);
        Optional<AuctionBid> highestOpt = auctions.findHighestBid(id);

        BigDecimal currentPrice;
        BigDecimal highestBid = null;
        BigDecimal minimumNext;
        boolean isHighest = false;
        String highestDisplay = null;
        if (highestOpt.isPresent()) {
            AuctionBid h = highestOpt.get();
            currentPrice = h.getAmount();
            highestBid = h.getAmount();
            minimumNext = h.getAmount().add(auction.getBidStep());
            isHighest = currentUser != null && h.getBidderId().equals(currentUser.getUserId());
            User u = em.find(User.class, h.getBidderId());
            highestDisplay = u == null ? null : AuctionNames.anonymize(u.getFullName());
        } else {
            currentPrice = auction.getStartingPrice();
            minimumNext = auction.getStartingPrice().max(BigDecimal.ONE);
        }

        boolean ended = auction.getStatus() == AuctionStatus.ENDED;
        boolean hasWinner = ended && auction.getWinnerId() != null;
        boolean isWinner = hasWinner && currentUser != null && auction.getWinnerId().equals(currentUser.getUserId());

        String state = "NOT_APPLICABLE";
        Long winnerOrderId = null;
        if (ended) {
            if (auction.getWinnerId() == null) {
                state = "NO_BIDS";
            } else {
                Optional<Order> order = orders.findLatestByAuctionId(id);
                if (order.isPresent()) {
                    state = "ORDER_CREATED";
                    winnerOrderId = order.get().getOrderId();
                } else if (auction.getWinnerCheckoutDeadline() != null && now.isAfter(auction.getWinnerCheckoutDeadline())) {
                    state = "EXPIRED";
                } else {
                    state = "WAITING";
                }
            }
        }
        return new AuctionDetailResponse(id, auction.getProductId(), auction.getStatus().name(),
                auction.getStartingPrice(), auction.getBidStep(), currentPrice, highestBid, bidCount, minimumNext,
                auction.getStartsAt(), auction.getEndsAt(), hasWinner, auction.getFinalPrice(),
                auction.getWinnerCheckoutDeadline(), isWinner, isHighest, highestDisplay, now, state, winnerOrderId);
    }

    private static String thumbnail(Product p) {
        List<ProductImage> images = p.getImages();
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.stream().filter(i -> Boolean.TRUE.equals(i.getIsThumbnail())).findFirst()
                .orElse(images.get(0)).getImageUrl();
    }
}
