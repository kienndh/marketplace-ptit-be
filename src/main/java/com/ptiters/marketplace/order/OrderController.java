package com.ptiters.marketplace.order;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderDetailResponse> createOrder(@RequestBody CreateOrderRequest payload) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createDirectOrder(null, payload));
    }

    @GetMapping("/me/buying")
    public ResponseEntity<OrderCursorPage> listBuyingOrders(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ResponseEntity.ok(orderService.listBuyingOrders(null, status, cursor, limit));
    }

    @GetMapping("/me/selling")
    public ResponseEntity<OrderCursorPage> listSellingOrders(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ResponseEntity.ok(orderService.listSellingOrders(null, status, cursor, limit));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailResponse> getOrderDetail(@PathVariable("orderId") Long orderId) {
        return ResponseEntity.ok(orderService.getOrderDetail(orderId, null));
    }

    @PatchMapping("/{orderId}/terms")
    public ResponseEntity<OrderDetailResponse> updateOrderTerms(
            @PathVariable("orderId") Long orderId,
            @RequestBody UpdateOrderTransactionTermsRequest payload) {
        return ResponseEntity.ok(orderService.updateTerms(orderId, null, payload));
    }

    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<OrderDetailResponse> confirmOrder(
            @PathVariable("orderId") Long orderId,
            @RequestBody ConfirmOrderRequest payload) {
        return ResponseEntity.ok(orderService.confirmOrder(orderId, null, payload));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderDetailResponse> cancelOrder(
            @PathVariable("orderId") Long orderId,
            @RequestBody CancelOrderRequest payload) {
        return ResponseEntity.ok(orderService.cancelOrder(orderId, null, payload));
    }
}
