package com.ptiters.marketplace.review;

import tools.jackson.databind.JsonNode;
import com.ptiters.marketplace.user.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping("/api/v1/orders/{orderId}/review")
    public ResponseEntity<OrderReviewResponse> createOrderReview(
            @PathVariable("orderId") Long orderId,
            @RequestBody JsonNode payload) {
        var user = currentUserResolver.getCurrentActiveUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.createOrderReview(orderId, user, payload));
    }

    /** Chưa có đánh giá → 200 với body JSON {@code null} (giống Python {@code Optional[OrderReviewResponse]}). */
    @GetMapping("/api/v1/orders/{orderId}/review")
    public ResponseEntity<Object> getOrderReview(@PathVariable("orderId") Long orderId) {
        var user = currentUserResolver.getCurrentActiveUser();
        OrderReviewResponse review = reviewService.getOrderReview(orderId, user);
        if (review == null) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("null");
        }
        return ResponseEntity.ok(review);
    }

    @PatchMapping("/api/v1/orders/{orderId}/review")
    public ResponseEntity<OrderReviewResponse> updateOrderReview(
            @PathVariable("orderId") Long orderId,
            @RequestBody JsonNode payload) {
        var user = currentUserResolver.getCurrentActiveUser();
        return ResponseEntity.ok(reviewService.updateOrderReview(orderId, user, payload));
    }

    @GetMapping("/api/v1/users/{publicId}/reviews")
    public ResponseEntity<PublicSellerReviewsCursorPage> getPublicSellerReviews(
            @PathVariable("publicId") UUID publicId,
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestParam(name = "cursor", required = false) String cursor) {
        return ResponseEntity.ok(reviewService.getPublicSellerReviews(publicId, limit, cursor));
    }
}
