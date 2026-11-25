package com.ptiters.marketplace.review;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/api/v1/orders/{orderId}/review")
    public ResponseEntity<OrderReviewResponse> createOrderReview(
            @PathVariable("orderId") Long orderId,
            @RequestBody ReviewCreateRequest payload) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.createOrderReview(orderId, null, payload));
    }

    @GetMapping("/api/v1/orders/{orderId}/review")
    public ResponseEntity<OrderReviewResponse> getOrderReview(@PathVariable("orderId") Long orderId) {
        return ResponseEntity.ok(reviewService.getOrderReview(orderId, null));
    }

    @PatchMapping("/api/v1/orders/{orderId}/review")
    public ResponseEntity<OrderReviewResponse> updateOrderReview(
            @PathVariable("orderId") Long orderId,
            @RequestBody ReviewUpdateRequest payload) {
        return ResponseEntity.ok(reviewService.updateOrderReview(orderId, null, payload));
    }

    @GetMapping("/api/v1/users/{publicId}/reviews")
    public ResponseEntity<PublicSellerReviewsCursorPage> getPublicSellerReviews(
            @PathVariable("publicId") UUID publicId,
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestParam(name = "cursor", required = false) String cursor) {
        return ResponseEntity.ok(reviewService.getPublicSellerReviews(publicId, limit, cursor));
    }
}
