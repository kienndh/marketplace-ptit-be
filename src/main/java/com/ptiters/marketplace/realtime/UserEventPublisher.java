package com.ptiters.marketplace.realtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Đẩy sự kiện realtime riêng tư tới từng user (WebSocket + Redis pub/sub ở Track E).
 * Tương ứng các hàm {@code publish_*} trong {@code app/modules/orders/websocket.py}.
 *
 * <p>Chỉ {@link #publishUserEvent} và {@link #revokeUserSessions} là điểm mở rộng; các hàm tiện ích còn lại đã dựng
 * sẵn payload giống Python và uỷ quyền cho {@link #publishUserEvent}. Mọi lời gọi phải xảy ra SAU khi transaction commit.
 */
public interface UserEventPublisher {

    /** Python: {@code publish_user_event(user_id, event_data)}. */
    void publishUserEvent(Long userId, Map<String, Object> eventData);

    /** Python: {@code revoke_user_sessions(user_id)} — chỉ gọi SAU commit; best-effort. */
    void revokeUserSessions(Long userId);

    /** Python: {@code publish_order_created(seller_id, order_id)}. */
    default void publishOrderCreated(Long sellerId, Long orderId) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "ORDER_CREATED");
        event.put("order_id", orderId);
        publishUserEvent(sellerId, event);
    }

    /** Python: {@code publish_order_updated(buyer_id, seller_id, order_id, reason)}; seller nhận nếu khác buyer. */
    default void publishOrderUpdated(Long buyerId, Long sellerId, Long orderId, String reason) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "ORDER_UPDATED");
        event.put("order_id", orderId);
        event.put("reason", reason);
        publishUserEvent(buyerId, event);
        if (!sellerId.equals(buyerId)) {
            publishUserEvent(sellerId, event);
        }
    }

    /** Python: {@code publish_auction_winner_checked_out(seller_id, auction_id, order_id)}. */
    default void publishAuctionWinnerCheckedOut(Long sellerId, Long auctionId, Long orderId) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "AUCTION_WINNER_CHECKED_OUT");
        event.put("auction_id", auctionId);
        event.put("order_id", orderId);
        publishUserEvent(sellerId, event);
    }
}
