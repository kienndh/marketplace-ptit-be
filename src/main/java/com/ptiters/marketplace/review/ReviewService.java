package com.ptiters.marketplace.review;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ptiters.marketplace.exception.ApiException;
import com.ptiters.marketplace.notification.NotificationType;
import com.ptiters.marketplace.notification.NotificationWriter;
import com.ptiters.marketplace.order.Order;
import com.ptiters.marketplace.order.OrderRepository;
import com.ptiters.marketplace.order.OrderStatus;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserRepository;
import com.ptiters.marketplace.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** Tương ứng {@code app/modules/reviews/service.py}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private static final String ORDER_NOT_FOUND = "Không tìm thấy đơn hàng.";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ReviewRepository reviewRepository;
    private final ReviewQueryRepository reviewQueryRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final NotificationWriter notificationWriter;

    @Transactional
    public OrderReviewResponse createOrderReview(Long orderId, User user, JsonNode body) {
        var payload = ReviewPayloadParser.parse(body, true);
        Order order = getOrderForParticipant(orderId, user.getUserId());

        if (order.getSellerId().equals(user.getUserId())) {
            throw ApiException.forbidden("Chỉ người mua mới có thể đánh giá người bán.");
        }
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw ApiException.badRequest("Chỉ có thể đánh giá sau khi đơn hàng hoàn tất.");
        }
        if (order.getBuyerId().equals(order.getSellerId())) {
            throw ApiException.badRequest("Người bán không thể tự đánh giá chính mình.");
        }
        if (reviewQueryRepository.findByOrderAndReviewer(orderId, user.getUserId(), false).isPresent()) {
            throw ApiException.conflict("Bạn đã đánh giá đơn hàng này.");
        }

        Review review = Review.builder()
                .orderId(orderId)
                .reviewerId(user.getUserId())
                .revieweeId(order.getSellerId())
                .rating(payload.rating().shortValue())
                .comment(payload.comment())
                .build();
        try {
            review = reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            // Backstop của ràng buộc UNIQUE ux_reviews_order_reviewer (đua đồng thời).
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("ux_reviews_order_reviewer")) {
                throw ApiException.conflict("Bạn đã đánh giá đơn hàng này.");
            }
            throw e;
        }

        // Cùng transaction với review; dispatch realtime/push chạy SAU commit (NotificationWriter đảm nhiệm).
        notificationWriter.create(
                review.getRevieweeId(),
                NotificationType.NEW_REVIEW,
                "Bạn có đánh giá mới",
                "Đơn " + order.getOrderCode() + " vừa nhận được đánh giá " + review.getRating() + " sao.",
                "ORDER",
                orderId);

        return toResponse(review, user.getFullName());
    }

    @Transactional
    public OrderReviewResponse updateOrderReview(Long orderId, User user, JsonNode body) {
        var payload = ReviewPayloadParser.parse(body, false);
        Review review = reviewQueryRepository.findByOrderAndReviewer(orderId, user.getUserId(), true)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đánh giá cho đơn hàng này."));
        if (payload.ratingPresent()) {
            review.setRating(payload.rating().shortValue());
        }
        if (payload.commentPresent()) {
            review.setComment(payload.comment());
        }
        review = reviewRepository.saveAndFlush(review);
        return toResponse(review, user.getFullName());
    }

    public OrderReviewResponse getOrderReview(Long orderId, User user) {
        getOrderForParticipant(orderId, user.getUserId());
        Review review = reviewQueryRepository.findByOrderId(orderId).orElse(null);
        if (review == null) {
            return null;
        }
        String reviewerName = userRepository.findById(review.getReviewerId())
                .map(User::getFullName).orElse("Người mua");
        return toResponse(review, reviewerName);
    }

    public PublicSellerReviewsCursorPage getPublicSellerReviews(UUID sellerPublicId, int limit, String cursor) {
        if (limit < 1 || limit > 50) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "limit phải nằm trong khoảng 1..50.");
        }
        User seller = userRepository.findByPublicId(sellerPublicId)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng."));

        LocalDateTime cursorAt = null;
        Long cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            Object[] decoded = decodeCursor(cursor);
            cursorAt = (LocalDateTime) decoded[0];
            cursorId = (Long) decoded[1];
        }

        List<Review> reviews = reviewQueryRepository.findSellerReviews(seller.getUserId(), limit + 1, cursorAt, cursorId);
        boolean hasMore = reviews.size() > limit;
        List<Review> page = hasMore ? reviews.subList(0, limit) : reviews;
        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            Review last = page.get(page.size() - 1);
            nextCursor = encodeCursor(last.getCreatedAt(), last.getReviewId());
        }
        List<PublicSellerReviewItem> items = page.stream()
                .map(r -> new PublicSellerReviewItem(
                        (int) r.getRating(), r.getComment(), true, r.getCreatedAt(), r.getUpdatedAt()))
                .toList();
        return new PublicSellerReviewsCursorPage(items, nextCursor, hasMore);
    }

    // ------------------------------------------------------------------ helpers

    private Order getOrderForParticipant(Long orderId, Long userId) {
        return orderRepository.findById(orderId)
                .filter(o -> o.getBuyerId().equals(userId) || o.getSellerId().equals(userId))
                .orElseThrow(() -> ApiException.notFound(ORDER_NOT_FOUND));
    }

    private OrderReviewResponse toResponse(Review r, String reviewerName) {
        return new OrderReviewResponse(r.getReviewId(), r.getOrderId(), r.getReviewerId(), r.getRevieweeId(),
                (int) r.getRating(), r.getComment(), reviewerName, r.getCreatedAt(), r.getUpdatedAt());
    }

    /** Cursor giống Python: base64url của JSON {"created_at": iso, "review_id": n}. */
    static String encodeCursor(LocalDateTime createdAt, Long reviewId) {
        String raw = "{\"created_at\": \"" + createdAt + "\", \"review_id\": " + reviewId + "}";
        return Base64.getUrlEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static Object[] decodeCursor(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            JsonNode node = MAPPER.readTree(raw);
            return new Object[]{LocalDateTime.parse(node.get("created_at").asText()), node.get("review_id").asLong()};
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Định dạng con trỏ phân trang không hợp lệ.");
        }
    }
}
