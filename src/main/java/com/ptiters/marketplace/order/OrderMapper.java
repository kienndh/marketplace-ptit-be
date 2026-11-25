package com.ptiters.marketplace.order;

import com.ptiters.marketplace.user.SupportedBank;
import com.ptiters.marketplace.user.SupportedBanksRegistry;
import com.ptiters.marketplace.user.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

/** Port {@code format_order_summary}, {@code format_order_detail}, {@code generate_vietqr_url} (orders/service.py). */
@Component
public class OrderMapper {

    public OrderItemResponse toItem(OrderItem item) {
        return new OrderItemResponse(item.getOrderItemId(), item.getProductId(), item.getProductTitle(),
                item.getProductImageUrl(), item.getQuantity(), item.getPriceAtPurchase());
    }

    /** {@code primary} = item đầu tiên của đơn (null → item rỗng giống Python). */
    public OrderSummaryResponse toSummary(Order o, OrderItem primary) {
        OrderItemResponse item = primary != null
                ? toItem(primary)
                : new OrderItemResponse(0L, 0L, "", null, 1, BigDecimal.ZERO);
        return new OrderSummaryResponse(o.getOrderId(), o.getOrderCode(), o.getBuyerId(), o.getSellerId(),
                o.getStatus().name(), o.getPaymentMethod().name(), o.getPaymentStatus().name(),
                o.getFulfillmentMethod().name(), o.getTotalAmount(), item, o.getCreatedAt(), o.getUpdatedAt());
    }

    public OrderDetailResponse toDetail(Order o, User buyer, User seller) {
        List<OrderItemResponse> items = o.getItems().stream().map(this::toItem).toList();
        List<OrderStatusHistoryResponse> history = o.getStatusHistory().stream()
                .sorted(Comparator.comparing(OrderStatusHistory::getCreatedAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(OrderStatusHistory::getHistoryId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(h -> new OrderStatusHistoryResponse(h.getHistoryId(),
                        h.getFromStatus() == null ? null : h.getFromStatus().name(),
                        h.getToStatus().name(), h.getChangedBy(), h.getNote(), h.getCreatedAt()))
                .toList();
        return new OrderDetailResponse(
                o.getOrderId(), o.getOrderCode(),
                o.getBuyerId(), buyer == null ? null : buyer.getPublicId(),
                buyer == null ? "User " + o.getBuyerId() : buyer.getFullName(),
                buyer == null ? null : buyer.getAvatarUrl(),
                o.getSellerId(), seller == null ? null : seller.getPublicId(),
                seller == null ? "User " + o.getSellerId() : seller.getFullName(),
                seller == null ? null : seller.getAvatarUrl(),
                o.getStatus().name(), o.getPaymentMethod().name(), o.getPaymentStatus().name(),
                o.getPaymentReceiptUrl(), o.getReceiptUploadedAt(), o.getPaidConfirmedAt(), o.getPaymentNote(),
                buildPaymentBank(o),
                o.getFulfillmentMethod().name(), o.getMeetupPlace(), o.getMeetupTime(), o.getShippingAddress(),
                o.getShippingExpectedAt(), o.getContactName(), o.getContactPhone(), o.getBuyerNote(),
                o.getTotalAmount(), o.getAutoCancelAt(), o.getConfirmedAt(), o.getCompletedAt(), o.getCancelledAt(),
                o.getCancelledBy() == null ? null : o.getCancelledBy().name(), o.getCancelReason(),
                items, history, o.getCreatedAt(), o.getUpdatedAt());
    }

    /** Chỉ lộ snapshot ngân hàng + VietQR cho đơn BANK_TRANSFER có phí đã CONFIRMED/DELIVERING/COMPLETED. */
    private OrderPaymentBankResponse buildPaymentBank(Order o) {
        boolean visibleStatus = o.getStatus() == OrderStatus.CONFIRMED
                || o.getStatus() == OrderStatus.DELIVERING
                || o.getStatus() == OrderStatus.COMPLETED;
        if (o.getTotalAmount().signum() <= 0
                || o.getPaymentMethod() != PaymentMethod.BANK_TRANSFER
                || !visibleStatus
                || isBlank(o.getPaymentBankBin())
                || isBlank(o.getPaymentBankAccountNumber())) {
            return null;
        }
        SupportedBank bank = SupportedBanksRegistry.getBankByBin(o.getPaymentBankBin()).orElse(null);
        String accountName = o.getPaymentBankAccountName() == null ? "" : o.getPaymentBankAccountName();
        String qrUrl = generateVietQrUrl(o.getPaymentBankBin(), o.getPaymentBankAccountNumber(), o.getTotalAmount(),
                o.getOrderCode(), accountName);
        return new OrderPaymentBankResponse(
                o.getPaymentBankBin(),
                bank != null ? bank.getCode() : o.getPaymentBankBin(),
                bank != null ? bank.getName() : "",
                bank != null ? bank.getShortName() : o.getPaymentBankBin(),
                o.getPaymentBankAccountNumber(), accountName, qrUrl);
    }

    /** Python: {@code generate_vietqr_url} — URL-encode an toàn (quote path + urlencode query). */
    public static String generateVietQrUrl(String bankBin, String accountNumber, BigDecimal amount, String orderCode,
                                           String accountName) {
        String safeBin = encode(bankBin.trim());
        String safeAccount = encode(accountNumber.trim());
        String query = "amount=" + encode(String.valueOf(amount.toBigInteger()))
                + "&addInfo=" + encode(orderCode.trim())
                + "&accountName=" + encode(accountName.trim());
        return "https://img.vietqr.io/image/" + safeBin + "-" + safeAccount + "-compact2.png?" + query;
    }

    /** Khớp {@code urllib.parse.quote_plus}: khoảng trắng → '+', '*' → %2A, '~' giữ nguyên. */
    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("*", "%2A").replace("%7E", "~");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
