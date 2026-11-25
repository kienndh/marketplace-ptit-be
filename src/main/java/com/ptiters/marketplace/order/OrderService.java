package com.ptiters.marketplace.order;

import com.ptiters.marketplace.exception.ApiException;
import com.ptiters.marketplace.integration.MediaStorage;
import com.ptiters.marketplace.integration.MediaStorageException;
import com.ptiters.marketplace.integration.MediaStorageNotConfiguredException;
import com.ptiters.marketplace.notification.NotificationType;
import com.ptiters.marketplace.notification.NotificationWriter;
import com.ptiters.marketplace.product.ListingType;
import com.ptiters.marketplace.product.Product;
import com.ptiters.marketplace.product.ProductApprovalStatus;
import com.ptiters.marketplace.product.ProductImage;
import com.ptiters.marketplace.product.ProductModerationStatus;
import com.ptiters.marketplace.product.ProductStatus;
import com.ptiters.marketplace.realtime.MediaValidator;
import com.ptiters.marketplace.realtime.UserEventPublisher;
import com.ptiters.marketplace.user.CapabilityService;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserRepository;
import com.ptiters.marketplace.user.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nghiệp vụ đơn hàng (port {@code app/modules/orders/service.py}).
 *
 * <p>Chống oversell: khoá bi quan {@code SELECT ... FOR UPDATE} trên hàng {@code products} (và {@code orders}) trước khi
 * đổi {@code reserved_stock}/{@code stock}. Notification ghi trong cùng transaction qua {@link NotificationWriter}
 * (realtime/push chạy SAU commit); realtime đơn hàng chạy SAU commit qua {@link PostCommit}.
 */
@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final String NOT_FOUND = "Không tìm thấy đơn hàng.";
    private static final String PRODUCT_NOT_AVAILABLE = "Sản phẩm không tồn tại hoặc không khả dụng để đặt mua.";
    private static final long RECEIPT_MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> RECEIPT_MIMES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern CURSOR_CREATED = Pattern.compile("\"created_at\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CURSOR_ID = Pattern.compile("\"order_id\"\\s*:\\s*(\\d+)");

    /** Điều khoản giao dịch của đơn sinh từ đấu giá (Python: {@code WinnerCheckoutRequest}). */
    public record AuctionOrderTerms(PaymentMethod paymentMethod, FulfillmentMethod fulfillmentMethod,
                                    String meetupPlace, LocalDateTime meetupTime, String shippingAddress,
                                    String contactName, String contactPhone, String buyerNote) {
    }

    private final OrderQueryRepository orders;
    private final OrderMapper mapper;
    private final NotificationWriter notifications;
    private final UserEventPublisher userEvents;
    private final CapabilityService capabilityService;
    private final MediaStorage mediaStorage;
    private final MediaValidator mediaValidator;
    private final UserRepository users;

    @PersistenceContext
    private EntityManager em;

    @Value("${marketplace.order.confirm-timeout-hours:24}")
    private int confirmTimeoutHours;

    public OrderService(OrderQueryRepository orders, OrderMapper mapper, NotificationWriter notifications,
                        UserEventPublisher userEvents, CapabilityService capabilityService, MediaStorage mediaStorage,
                        MediaValidator mediaValidator, UserRepository users) {
        this.orders = orders;
        this.mapper = mapper;
        this.notifications = notifications;
        this.userEvents = userEvents;
        this.capabilityService = capabilityService;
        this.mediaStorage = mediaStorage;
        this.mediaValidator = mediaValidator;
        this.users = users;
    }

    // ================================================================== create

    @Transactional
    public OrderDetailResponse createDirectOrder(User buyer, CreateOrderRequest p) {
        if (buyer.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("Tài khoản của bạn không được phép đặt hàng.");
        }
        if (!capabilityService.getUserAccessStatus(buyer).isCanBuy()) {
            throw CapabilityRestrictedException.buy();
        }

        FulfillmentMethod fulfillment = p.fulfillmentOrDefault();
        if (fulfillment == FulfillmentMethod.MEETUP && isBlank(p.meetupPlace())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Vui lòng nhập địa điểm hẹn gặp.");
        }
        if (fulfillment == FulfillmentMethod.SHIPPING && isBlank(p.shippingAddress())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Vui lòng nhập địa chỉ giao hàng.");
        }
        LocalDateTime meetupTime = DateTimes.parseToUtcNaive(p.meetupTime(), "meetup_time");
        LocalDateTime shippingExpectedAt = DateTimes.parseToUtcNaive(p.shippingExpectedAt(), "shipping_expected_at");

        // 1. Khoá hàng sản phẩm (chống oversell)
        Product product = em.find(Product.class, p.productId(), LockModeType.PESSIMISTIC_WRITE);
        if (product == null || Boolean.TRUE.equals(product.getIsDeleted())
                || product.getStatus() != ProductStatus.AVAILABLE
                || product.getModerationStatus() == ProductModerationStatus.BLOCKED
                || product.getApprovalStatus() != ProductApprovalStatus.APPROVED) {
            throw ApiException.notFound(PRODUCT_NOT_AVAILABLE);
        }

        Long sellerId = product.getSeller().getUserId();
        User seller = em.find(User.class, sellerId);
        if (seller == null || seller.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.badRequest("Người bán hiện đang bị khóa tài khoản hoặc không hoạt động.");
        }
        // Privacy-safe: người bán bị hạn chế SELL → 404
        if (!capabilityService.getUserAccessStatus(seller).isCanSell()) {
            throw ApiException.notFound(PRODUCT_NOT_AVAILABLE);
        }
        if (sellerId.equals(buyer.getUserId())) {
            throw ApiException.badRequest("Không thể mua sản phẩm của chính mình.");
        }
        if (product.getListingType() != ListingType.FIXED_PRICE) {
            throw ApiException.badRequest("Sản phẩm đấu giá không thể mua trực tiếp qua chức năng này.");
        }

        // 2. Tồn kho khả dụng
        int quantity = p.quantityOrDefault();
        int available = product.getStock() - product.getReservedStock();
        if (quantity > available) {
            throw ApiException.badRequest("Số lượng tồn kho khả dụng không đủ (còn " + available + " sản phẩm).");
        }

        // 3. Giữ hàng
        product.setReservedStock(product.getReservedStock() + quantity);

        BigDecimal priceAtPurchase = product.getPrice();
        BigDecimal total = priceAtPurchase.multiply(BigDecimal.valueOf(quantity));
        LocalDateTime now = DateTimes.nowUtc();

        // 4. Phương thức/trạng thái thanh toán
        PaymentMethod paymentMethod;
        PaymentStatus paymentStatus;
        LocalDateTime paidConfirmedAt;
        if (total.signum() == 0) {
            paymentMethod = PaymentMethod.OTHER;   // sentinel nội bộ cho đơn miễn phí
            paymentStatus = PaymentStatus.PAID;
            paidConfirmedAt = now;
        } else {
            if (p.paymentMethod() == null) {
                throw ApiException.badRequest("Vui lòng chọn phương thức thanh toán cho sản phẩm có phí.");
            }
            if (p.paymentMethod() == PaymentMethod.OTHER) {
                throw ApiException.badRequest("Phương thức thanh toán không hợp lệ cho sản phẩm có phí.");
            }
            paymentMethod = p.paymentMethod();
            paymentStatus = PaymentStatus.UNPAID;
            paidConfirmedAt = null;
        }

        Order order = Order.builder()
                .orderCode(generateUniqueOrderCode())
                .buyerId(buyer.getUserId())
                .sellerId(sellerId)
                .status(OrderStatus.PENDING)
                .paymentMethod(paymentMethod)
                .paymentStatus(paymentStatus)
                .fulfillmentMethod(fulfillment)
                .meetupPlace(trimToNull(p.meetupPlace()))
                .meetupTime(meetupTime)
                .shippingAddress(trimToNull(p.shippingAddress()))
                .shippingExpectedAt(shippingExpectedAt)
                .contactName(p.contactName().trim())
                .contactPhone(p.contactPhone().trim())
                .buyerNote(trimToNull(p.buyerNote()))
                .totalAmount(total)
                .autoCancelAt(now.plusHours(confirmTimeoutHours))
                .paidConfirmedAt(paidConfirmedAt)
                .build();
        OrderItem item = OrderItem.builder()
                .productId(product.getProductId())
                .productTitle(product.getTitle())
                .productImageUrl(resolveImageUrl(product))
                .quantity(quantity)
                .priceAtPurchase(priceAtPurchase)
                .order(order)
                .build();
        order.getItems().add(item);
        addHistory(order, null, OrderStatus.PENDING, buyer.getUserId(), "Đặt hàng thành công");
        em.persist(order);
        em.flush();

        notifications.create(sellerId, NotificationType.NEW_ORDER, "Bạn có đơn hàng mới",
                "Đơn " + order.getOrderCode() + " vừa được tạo.", "ORDER", order.getOrderId());
        Long orderId = order.getOrderId();
        PostCommit.run("order-created", () -> userEvents.publishOrderCreated(sellerId, orderId));
        return detail(order);
    }

    /**
     * Sinh đơn từ kết quả đấu giá: KHÔNG tăng {@code reserved_stock} lần 2 (đã giữ khi đóng phiên).
     * Chạy trong transaction của caller (AuctionService.winnerCheckout). Realtime do caller phát sau commit.
     */
    @Transactional
    public Order createAuctionWinnerOrder(User winner, Long auctionId, Product product, BigDecimal finalPrice,
                                          AuctionOrderTerms t, LocalDateTime now) {
        if (t.paymentMethod() != PaymentMethod.CASH_ON_MEETUP && t.paymentMethod() != PaymentMethod.BANK_TRANSFER) {
            throw ApiException.badRequest("Phương thức thanh toán không hợp lệ.");
        }
        Order order = Order.builder()
                .orderCode(generateUniqueOrderCode())
                .buyerId(winner.getUserId())
                .sellerId(product.getSeller().getUserId())
                .auctionId(auctionId)
                .status(OrderStatus.PENDING)
                .paymentMethod(t.paymentMethod())
                .paymentStatus(PaymentStatus.UNPAID)
                .fulfillmentMethod(t.fulfillmentMethod())
                .meetupPlace(trimToNull(t.meetupPlace()))
                .meetupTime(t.meetupTime())
                .shippingAddress(trimToNull(t.shippingAddress()))
                .contactName(t.contactName().trim())
                .contactPhone(t.contactPhone().trim())
                .buyerNote(trimToNull(t.buyerNote()))
                .totalAmount(finalPrice)
                .autoCancelAt(now.plusHours(confirmTimeoutHours))
                .build();
        OrderItem item = OrderItem.builder()
                .productId(product.getProductId())
                .productTitle(product.getTitle())
                .productImageUrl(resolveImageUrl(product))
                .quantity(1)
                .priceAtPurchase(finalPrice)
                .order(order)
                .build();
        order.getItems().add(item);
        addHistory(order, null, OrderStatus.PENDING, winner.getUserId(), "Đặt mua thành công từ kết quả trúng đấu giá");
        em.persist(order);
        em.flush();
        notifications.create(order.getSellerId(), NotificationType.NEW_ORDER, "Bạn có đơn hàng mới",
                "Đơn " + order.getOrderCode() + " vừa được tạo.", "ORDER", order.getOrderId());
        return order;
    }

    // ================================================================== read

    public OrderDetailResponse getOrderDetail(Long orderId, User currentUser) {
        Order order = orders.findById(orderId).orElse(null);
        if (order == null || !isParticipant(order, currentUser)) {
            throw ApiException.notFound(NOT_FOUND);
        }
        return detail(order);
    }

    public OrderCursorPage listBuyingOrders(User buyer, String statusFilter, String cursor, int limit) {
        return list(buyer.getUserId(), true, statusFilter, cursor, limit);
    }

    public OrderCursorPage listSellingOrders(User seller, String statusFilter, String cursor, int limit) {
        return list(seller.getUserId(), false, statusFilter, cursor, limit);
    }

    private OrderCursorPage list(Long userId, boolean buying, String statusFilter, String cursor, int limit) {
        int lim = Math.max(1, Math.min(limit, 50));
        OrderStatus status = null;
        if (statusFilter != null && !statusFilter.isBlank()) {
            try {
                status = OrderStatus.valueOf(statusFilter.trim());
            } catch (IllegalArgumentException e) {
                // Python: so khớp chuỗi với cột status → trạng thái lạ cho kết quả rỗng
                return new OrderCursorPage(List.of(), null, false);
            }
        }
        LocalDateTime cAt = null;
        Long cId = null;
        if (cursor != null && !cursor.isBlank()) {
            Object[] decoded = decodeCursor(cursor);   // cursor hỏng → bỏ qua (Python trả None)
            if (decoded != null) {
                cAt = (LocalDateTime) decoded[0];
                cId = (Long) decoded[1];
            }
        }
        List<Order> rows = buying
                ? orders.listByBuyer(userId, status, cAt, cId, lim)
                : orders.listBySeller(userId, status, cAt, cId, lim);
        boolean hasMore = rows.size() > lim;
        List<Order> page = hasMore ? rows.subList(0, lim) : rows;

        Map<Long, OrderItem> primaryItems = new HashMap<>();
        for (OrderItem i : orders.findItemsByOrderIds(page.stream().map(Order::getOrderId).toList())) {
            primaryItems.putIfAbsent(i.getOrder().getOrderId(), i);
        }
        String next = null;
        if (hasMore && !page.isEmpty()) {
            Order last = page.get(page.size() - 1);
            next = encodeCursor(last.getCreatedAt(), last.getOrderId());
        }
        return new OrderCursorPage(
                page.stream().map(o -> mapper.toSummary(o, primaryItems.get(o.getOrderId()))).toList(), next, hasMore);
    }

    // ================================================================== seller actions

    @Transactional
    public OrderDetailResponse confirmOrder(Long orderId, User seller, ConfirmOrderRequest payload) {
        LocalDateTime now = DateTimes.nowUtc();
        Order order = lockOrder(orderId);
        if (!order.getSellerId().equals(seller.getUserId())) {
            throw ApiException.notFound(NOT_FOUND);
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw ApiException.badRequest("Chỉ có thể xác nhận đơn hàng đang ở trạng thái chờ xác nhận.");
        }
        if (order.getPaymentMethod() == PaymentMethod.BANK_TRANSFER && order.getTotalAmount().signum() > 0) {
            requireBankProfile(seller, "Bạn cần thiết lập tài khoản nhận tiền trước khi xác nhận đơn chuyển khoản.");
            snapshotBank(order, seller);
        }
        order.setStatus(OrderStatus.CONFIRMED);
        order.setConfirmedAt(now);
        order.setAutoCancelAt(null);
        if (payload != null && !isBlank(payload.paymentNote())) {
            order.setPaymentNote(payload.paymentNote().trim());
        }
        addHistory(order, OrderStatus.PENDING, OrderStatus.CONFIRMED, seller.getUserId(), "Người bán đã xác nhận đơn hàng");
        em.flush();
        notifyOrder(order.getBuyerId(), "Đơn hàng đã được xác nhận",
                "Đơn " + order.getOrderCode() + " đã được người bán xác nhận.", order);
        publishUpdated(order, "CONFIRMED");
        return detail(order);
    }

    @Transactional
    public OrderDetailResponse confirmPayment(Long orderId, User seller) {
        LocalDateTime now = DateTimes.nowUtc();
        Order order = lockOrder(orderId);
        if (!order.getSellerId().equals(seller.getUserId())) {
            throw ApiException.notFound(NOT_FOUND);
        }
        if (order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.DELIVERING) {
            throw ApiException.badRequest("Đơn hàng phải ở trạng thái đã xác nhận hoặc đang giao để xác nhận thanh toán.");
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return detail(order);   // idempotent
        }
        switch (order.getPaymentMethod()) {
            case BANK_TRANSFER -> {
                if (order.getStatus() != OrderStatus.CONFIRMED) {
                    throw ApiException.badRequest("Đơn hàng chuyển khoản phải ở trạng thái đã xác nhận để xác nhận thanh toán.");
                }
                if (order.getPaymentStatus() != PaymentStatus.VERIFYING || order.getPaymentReceiptUrl() == null) {
                    throw ApiException.badRequest("Không thể xác nhận thanh toán chuyển khoản khi người mua chưa tải lên biên nhận.");
                }
            }
            case CASH_ON_MEETUP -> {
                if (order.getFulfillmentMethod() == FulfillmentMethod.MEETUP && order.getStatus() != OrderStatus.CONFIRMED) {
                    throw ApiException.badRequest("Đơn hàng gặp mặt trực tiếp phải ở trạng thái đã xác nhận để xác nhận thanh toán.");
                }
                if (order.getPaymentStatus() != PaymentStatus.UNPAID) {
                    throw ApiException.badRequest("Không thể xác nhận thanh toán tiền mặt với trạng thái hiện tại.");
                }
            }
            case OTHER -> {
                if (order.getTotalAmount().signum() > 0) {
                    throw ApiException.badRequest("Không thể xác nhận thanh toán với trạng thái hiện tại.");
                }
            }
        }
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaidConfirmedAt(now);
        addHistory(order, order.getStatus(), order.getStatus(), seller.getUserId(), "Người bán xác nhận đã nhận đủ thanh toán");
        em.flush();
        notifyOrder(order.getBuyerId(), "Thanh toán đã được xác nhận",
                "Thanh toán cho đơn " + order.getOrderCode() + " đã được người bán xác nhận.", order);
        publishUpdated(order, "PAYMENT_CONFIRMED");
        return detail(order);
    }

    @Transactional
    public OrderDetailResponse markDelivering(Long orderId, User seller) {
        Order order = lockOrder(orderId);
        if (!order.getSellerId().equals(seller.getUserId())) {
            throw ApiException.notFound(NOT_FOUND);
        }
        if (order.getFulfillmentMethod() != FulfillmentMethod.SHIPPING) {
            throw ApiException.badRequest("Hình thức gặp mặt trực tiếp (MEETUP) không sử dụng trạng thái đang giao hàng.");
        }
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw ApiException.badRequest("Chỉ có thể chuyển sang trạng thái đang giao từ trạng thái đã xác nhận.");
        }
        if (order.getPaymentMethod() == PaymentMethod.BANK_TRANSFER && order.getPaymentStatus() != PaymentStatus.PAID) {
            throw ApiException.badRequest("Đơn hàng chuyển khoản giao qua bưu điện/vận chuyển bắt buộc phải thanh toán trước khi giao.");
        }
        order.setStatus(OrderStatus.DELIVERING);
        addHistory(order, OrderStatus.CONFIRMED, OrderStatus.DELIVERING, seller.getUserId(), "Người bán đã bắt đầu giao hàng");
        em.flush();
        notifyOrder(order.getBuyerId(), "Đơn hàng đang được giao",
                "Đơn " + order.getOrderCode() + " đã bắt đầu được giao đến bạn.", order);
        publishUpdated(order, "DELIVERING");
        return detail(order);
    }

    @Transactional
    public OrderDetailResponse updateTransactionTerms(Long orderId, User seller, UpdateOrderTransactionTermsRequest p) {
        Order order = lockOrder(orderId);
        if (!order.getSellerId().equals(seller.getUserId())) {
            throw ApiException.notFound(NOT_FOUND);
        }
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw ApiException.badRequest("Chỉ có thể chỉnh sửa điều khoản giao dịch khi đơn hàng ở trạng thái chờ xác nhận hoặc đã xác nhận.");
        }
        List<Object> before = termsSnapshot(order);

        // 1. Phương thức thanh toán (đơn miễn phí giữ sentinel OTHER)
        if (order.getTotalAmount().signum() != 0 && p.getPaymentMethod() != null) {
            PaymentMethod newMethod = p.getPaymentMethod();
            if (newMethod == PaymentMethod.OTHER) {
                throw ApiException.badRequest("Phương thức thanh toán không hợp lệ cho đơn hàng có phí.");
            }
            if (newMethod != order.getPaymentMethod()) {
                if (order.getPaymentStatus() == PaymentStatus.VERIFYING || order.getPaymentStatus() == PaymentStatus.PAID) {
                    throw ApiException.badRequest("Không thể thay đổi phương thức thanh toán khi người mua đã chuyển khoản hoặc thanh toán đã hoàn tất.");
                }
                if (newMethod == PaymentMethod.BANK_TRANSFER) {
                    requireBankProfile(seller, "Bạn cần thiết lập tài khoản nhận tiền trước khi chuyển đơn sang hình thức chuyển khoản.");
                    if (order.getStatus() == OrderStatus.CONFIRMED) {
                        snapshotBank(order, seller);
                    }
                } else if (newMethod == PaymentMethod.CASH_ON_MEETUP) {
                    order.setPaymentBankBin(null);
                    order.setPaymentBankAccountNumber(null);
                    order.setPaymentBankAccountName(null);
                }
                order.setPaymentMethod(newMethod);
            }
        }

        // 2. Hình thức giao nhận + địa điểm
        FulfillmentMethod newFulfillment = p.getFulfillmentMethod() != null ? p.getFulfillmentMethod() : order.getFulfillmentMethod();
        if (newFulfillment == FulfillmentMethod.MEETUP) {
            String place = p.getMeetupPlace() != null ? p.getMeetupPlace().trim() : order.getMeetupPlace();
            if (place == null || place.isEmpty()) {
                throw ApiException.badRequest("Vui lòng nhập địa điểm hẹn gặp.");
            }
            order.setFulfillmentMethod(FulfillmentMethod.MEETUP);
            order.setMeetupPlace(place);
            if (p.isMeetupTimeSet() || p.getMeetupTime() != null) {
                order.setMeetupTime(DateTimes.parseToUtcNaive(p.getMeetupTime(), "meetup_time"));
            }
            order.setShippingAddress(null);
            order.setShippingExpectedAt(null);
        } else if (newFulfillment == FulfillmentMethod.SHIPPING) {
            String address = p.getShippingAddress() != null ? p.getShippingAddress().trim() : order.getShippingAddress();
            if (address == null || address.isEmpty()) {
                throw ApiException.badRequest("Vui lòng nhập địa chỉ giao hàng.");
            }
            order.setFulfillmentMethod(FulfillmentMethod.SHIPPING);
            order.setShippingAddress(address);
            if (p.isShippingExpectedAtSet() || p.getShippingExpectedAt() != null) {
                order.setShippingExpectedAt(DateTimes.parseToUtcNaive(p.getShippingExpectedAt(), "shipping_expected_at"));
            }
            order.setMeetupPlace(null);
            order.setMeetupTime(null);
        }

        // 3. Ghi chú thanh toán
        if (p.getPaymentNote() != null) {
            order.setPaymentNote(trimToNull(p.getPaymentNote()));
        }

        boolean changed = !Objects.equals(before, termsSnapshot(order));
        em.flush();
        if (changed) {
            notifyOrder(order.getBuyerId(), "Điều khoản đơn hàng đã được cập nhật",
                    "Người bán đã cập nhật thông tin giao dịch của đơn " + order.getOrderCode() + ".", order);
        }
        publishUpdated(order, "TERMS_UPDATED");
        return detail(order);
    }

    // ================================================================== buyer action: receipt

    @Transactional
    public OrderDetailResponse uploadPaymentReceipt(Long orderId, User buyer, String filename, String contentType,
                                                    byte[] bytes) {
        LocalDateTime now = DateTimes.nowUtc();
        Order order = lockOrder(orderId);
        if (!order.getBuyerId().equals(buyer.getUserId())) {
            throw ApiException.notFound(NOT_FOUND);
        }
        if (order.getTotalAmount().signum() == 0) {
            throw ApiException.badRequest("Đơn hàng miễn phí không yêu cầu biên nhận thanh toán.");
        }
        if (order.getStatus() == OrderStatus.PENDING) {
            throw ApiException.badRequest("Đơn hàng phải được người bán xác nhận trước khi thanh toán chuyển khoản.");
        }
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw ApiException.badRequest("Chỉ có thể tải lên biên nhận thanh toán khi đơn hàng ở trạng thái đã xác nhận.");
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw ApiException.badRequest("Không thể thay đổi biên nhận sau khi người bán đã xác nhận thanh toán.");
        }
        if (order.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) {
            throw ApiException.badRequest("Chỉ áp dụng tải biên nhận cho phương thức chuyển khoản ngân hàng.");
        }
        String declared = contentType == null ? "image/jpeg" : contentType.toLowerCase(Locale.ROOT);
        if (!RECEIPT_MIMES.contains(declared)) {
            throw ApiException.badRequest("Định dạng ảnh không hợp lệ: '" + declared + "'. Chỉ hỗ trợ JPEG, PNG, WEBP.");
        }
        if (bytes == null || bytes.length == 0 || bytes.length > RECEIPT_MAX_BYTES) {
            throw ApiException.badRequest("Kích thước ảnh biên nhận không được vượt quá 5MB và không được rỗng.");
        }
        // Không tin Content-Type do client gửi: kiểm tra magic bytes (422 nếu không phải ảnh thật)
        mediaValidator.validateImageBytes(filename == null ? "receipt" : filename, bytes, RECEIPT_MAX_BYTES);

        MediaStorage.UploadedAsset asset;
        try {
            asset = mediaStorage.uploadOrderReceipt(bytes, filename, order.getOrderId());
        } catch (MediaStorageNotConfiguredException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ lưu trữ ảnh hiện chưa được cấu hình.");
        } catch (MediaStorageException e) {
            log.error("Receipt upload failed for order {}: {}", orderId, e.toString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Tải ảnh biên nhận thất bại. Vui lòng thử lại.");
        }
        order.setPaymentReceiptUrl(asset.secureUrl());
        order.setReceiptUploadedAt(now);
        order.setPaymentStatus(PaymentStatus.VERIFYING);
        addHistory(order, order.getStatus(), order.getStatus(), buyer.getUserId(),
                "Người mua đã tải lên biên nhận thanh toán chuyển khoản");
        em.flush();
        notifyOrder(order.getSellerId(), "Người mua đã gửi biên nhận",
                "Đơn " + order.getOrderCode() + " đang chờ bạn kiểm tra thanh toán.", order);
        publishUpdated(order, "RECEIPT_UPLOADED");
        return detail(order);
    }

    // ================================================================== complete / cancel

    @Transactional
    public OrderDetailResponse completeOrder(Long orderId, User currentUser) {
        LocalDateTime now = DateTimes.nowUtc();
        Order order = lockOrder(orderId);
        if (!isParticipant(order, currentUser)) {
            throw ApiException.notFound(NOT_FOUND);
        }
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw ApiException.badRequest("Không thể hoàn tất đơn hàng đã kết thúc.");
        }
        if (order.getFulfillmentMethod() == FulfillmentMethod.MEETUP) {
            if (order.getStatus() != OrderStatus.CONFIRMED) {
                throw ApiException.badRequest("Đơn hàng gặp mặt trực tiếp phải ở trạng thái đã xác nhận để hoàn tất.");
            }
        } else if (order.getStatus() != OrderStatus.DELIVERING) {
            throw ApiException.badRequest("Đơn hàng vận chuyển phải ở trạng thái đang giao để hoàn tất.");
        }
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            throw ApiException.badRequest("Đơn hàng phải hoàn tất thanh toán trước khi xác nhận hoàn thành.");
        }

        // Chốt tồn kho thực tế
        for (OrderItem item : order.getItems()) {
            Product product = em.find(Product.class, item.getProductId(), LockModeType.PESSIMISTIC_WRITE);
            if (product == null) {
                continue;
            }
            if (product.getReservedStock() < item.getQuantity() || product.getStock() < item.getQuantity()) {
                log.error("INVARIANT VIOLATION on completion: product {} stock={}, reserved={}, qty={}",
                        product.getProductId(), product.getStock(), product.getReservedStock(), item.getQuantity());
                throw stockInvariant("Lỗi dữ liệu tồn kho: số lượng tồn kho không hợp lệ.");
            }
            product.setStock(product.getStock() - item.getQuantity());
            product.setReservedStock(product.getReservedStock() - item.getQuantity());
            if (product.getStock() == 0) {
                product.setStatus(ProductStatus.SOLD_OUT);
                em.createQuery("delete from Favorite f where f.productId = :pid")
                        .setParameter("pid", product.getProductId())
                        .executeUpdate();
            }
        }

        OrderStatus old = order.getStatus();
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(now);
        addHistory(order, old, OrderStatus.COMPLETED, currentUser.getUserId(), "Đơn hàng đã hoàn tất giao dịch");
        em.flush();
        Long target = currentUser.getUserId().equals(order.getBuyerId()) ? order.getSellerId() : order.getBuyerId();
        notifyOrder(target, "Đơn hàng đã hoàn tất", "Đơn " + order.getOrderCode() + " đã được hoàn tất.", order);
        publishUpdated(order, "COMPLETED");
        return detail(order);
    }

    @Transactional
    public OrderDetailResponse cancelOrder(Long orderId, User currentUser, CancelOrderRequest payload) {
        LocalDateTime now = DateTimes.nowUtc();
        Order order = lockOrder(orderId);
        if (!isParticipant(order, currentUser)) {
            throw ApiException.notFound(NOT_FOUND);
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return detail(order);   // idempotent
        }
        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw ApiException.badRequest("Không thể hủy đơn hàng đã hoàn tất.");
        }
        if (order.getStatus() == OrderStatus.DELIVERING) {
            throw ApiException.badRequest("Không thể hủy đơn hàng đang trong quá trình giao hàng.");
        }
        if (order.getStatus() == OrderStatus.CONFIRMED
                && order.getTotalAmount().signum() > 0 && order.getPaymentStatus() != PaymentStatus.UNPAID) {
            throw ApiException.badRequest("Không thể hủy đơn hàng đã thanh toán hoặc đang xác thực biên nhận chuyển khoản.");
        }

        boolean byBuyer = currentUser.getUserId().equals(order.getBuyerId());
        releaseReservedStock(order, "cancellation");

        OrderStatus old = order.getStatus();
        String reason = payload.cancelReason().trim();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(now);
        order.setCancelledBy(byBuyer ? CancelledBy.BUYER : CancelledBy.SELLER);
        order.setCancelReason(reason);
        addHistory(order, old, OrderStatus.CANCELLED, currentUser.getUserId(), reason);
        em.flush();

        // lý do huỷ KHÔNG đưa vào nội dung thông báo
        Long target = byBuyer ? order.getSellerId() : order.getBuyerId();
        String body = (byBuyer ? "Người mua" : "Người bán") + " đã hủy đơn " + order.getOrderCode() + ".";
        notifyOrder(target, "Đơn hàng đã bị hủy", body, order);
        publishUpdated(order, "CANCELLED");
        return detail(order);
    }

    /** Worker: huỷ một đơn PENDING quá hạn. {@code false} = không còn đủ điều kiện (no-op, idempotent). */
    @Transactional
    public boolean autoCancelSingleOrder(Long orderId) {
        LocalDateTime now = DateTimes.nowUtc();
        Order order = orders.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING
                || order.getAutoCancelAt() == null || order.getAutoCancelAt().isAfter(now)) {
            return false;
        }
        releaseReservedStock(order, "auto-cancel");
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(now);
        order.setCancelledBy(CancelledBy.SYSTEM);
        order.setCancelReason("Hết hạn xác nhận từ người bán (tự động hủy)");
        addHistory(order, OrderStatus.PENDING, OrderStatus.CANCELLED, null, "System auto-cancel");
        em.flush();

        String body = "Đơn " + order.getOrderCode() + " đã tự động hủy do quá thời gian xác nhận.";
        notifyOrder(order.getBuyerId(), "Đơn hàng đã tự động hủy", body, order);
        notifyOrder(order.getSellerId(), "Đơn hàng đã tự động hủy", body, order);
        publishUpdated(order, "AUTO_CANCELLED");
        return true;
    }

    // ================================================================== helpers

    private Order lockOrder(Long orderId) {
        return orders.findByIdForUpdate(orderId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    private static boolean isParticipant(Order o, User u) {
        return u != null && (o.getBuyerId().equals(u.getUserId()) || o.getSellerId().equals(u.getUserId()));
    }

    private OrderDetailResponse detail(Order order) {
        return mapper.toDetail(order, users.findById(order.getBuyerId()).orElse(null),
                users.findById(order.getSellerId()).orElse(null));
    }

    private void addHistory(Order order, OrderStatus from, OrderStatus to, Long changedBy, String note) {
        order.getStatusHistory().add(OrderStatusHistory.builder()
                .order(order).fromStatus(from).toStatus(to).changedBy(changedBy).note(note).build());
    }

    private void notifyOrder(Long userId, String title, String body, Order order) {
        notifications.create(userId, NotificationType.ORDER_STATUS, title, body, "ORDER", order.getOrderId());
    }

    private void publishUpdated(Order order, String reason) {
        Long buyerId = order.getBuyerId();
        Long sellerId = order.getSellerId();
        Long orderId = order.getOrderId();
        PostCommit.run("order-updated:" + reason, () -> userEvents.publishOrderUpdated(buyerId, sellerId, orderId, reason));
    }

    /** Trả lại {@code reserved_stock} của từng item (khoá hàng sản phẩm trước khi đổi). */
    private void releaseReservedStock(Order order, String context) {
        for (OrderItem item : order.getItems()) {
            Product product = em.find(Product.class, item.getProductId(), LockModeType.PESSIMISTIC_WRITE);
            if (product == null) {
                continue;
            }
            if (product.getReservedStock() < item.getQuantity()) {
                log.error("INVARIANT VIOLATION on {}: product {} reserved={}, qty={}", context, product.getProductId(),
                        product.getReservedStock(), item.getQuantity());
                throw stockInvariant("Lỗi dữ liệu tồn kho: số lượng giữ hàng không hợp lệ.");
            }
            product.setReservedStock(product.getReservedStock() - item.getQuantity());
        }
    }

    private static ApiException stockInvariant(String message) {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    private static void requireBankProfile(User seller, String message) {
        if (isBlank(seller.getBankBin()) || isBlank(seller.getBankAccountNumber()) || isBlank(seller.getBankAccountName())) {
            throw ApiException.badRequest(message);
        }
    }

    private static void snapshotBank(Order order, User seller) {
        order.setPaymentBankBin(seller.getBankBin());
        order.setPaymentBankAccountNumber(seller.getBankAccountNumber());
        order.setPaymentBankAccountName(seller.getBankAccountName());
    }

    private static List<Object> termsSnapshot(Order o) {
        return java.util.Arrays.asList(o.getPaymentMethod(), o.getFulfillmentMethod(), o.getMeetupPlace(),
                o.getMeetupTime(), o.getShippingAddress(), o.getShippingExpectedAt(), o.getPaymentNote());
    }

    private static String resolveImageUrl(Product product) {
        List<ProductImage> images = product.getImages();
        if (images == null || images.isEmpty()) {
            return null;
        }
        for (ProductImage img : images) {
            if (Boolean.TRUE.equals(img.getIsThumbnail())) {
                return img.getImageUrl();
            }
        }
        return images.get(0).getImageUrl();
    }

    /** {@code ORD-yyMMdd-XXXXXX}; kiểm tra trùng trước khi chèn (unique {@code ux_orders_order_code}). */
    private String generateUniqueOrderCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            byte[] raw = new byte[3];
            RANDOM.nextBytes(raw);
            StringBuilder hex = new StringBuilder();
            for (byte b : raw) {
                hex.append(String.format("%02X", b));
            }
            String code = "ORD-" + DateTimes.nowUtc().format(CODE_DATE) + "-" + hex;
            if (!orders.existsByOrderCode(code)) {
                return code;
            }
        }
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể tạo mã đơn hàng duy nhất.");
    }

    static String encodeCursor(LocalDateTime createdAt, Long orderId) {
        String json = "{\"created_at\": \"" + createdAt + "\", \"order_id\": " + orderId + "}";
        return Base64.getUrlEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** @return {createdAt, orderId} hoặc null nếu cursor hỏng. */
    static Object[] decodeCursor(String cursor) {
        try {
            String json = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            Matcher c = CURSOR_CREATED.matcher(json);
            Matcher i = CURSOR_ID.matcher(json);
            if (!c.find() || !i.find()) {
                return null;
            }
            return new Object[]{LocalDateTime.parse(c.group(1)), Long.parseLong(i.group(1))};
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trimToNull(String s) {
        return s == null || s.isEmpty() ? null : s.trim();
    }
}
