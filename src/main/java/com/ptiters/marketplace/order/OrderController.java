package com.ptiters.marketplace.order;

import com.ptiters.marketplace.exception.ApiException;
import com.ptiters.marketplace.user.CurrentUserResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** REST đơn hàng — `/api/v1/orders` (API_CONTRACT mục 6). Mọi endpoint yêu cầu đăng nhập (ACTIVE). */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Validated
public class OrderController {

    private final OrderService orderService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping
    public ResponseEntity<OrderDetailResponse> createOrder(@Valid @RequestBody CreateOrderRequest payload) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.createDirectOrder(currentUserResolver.getCurrentActiveUser(), payload));
    }

    @GetMapping("/me/buying")
    public OrderCursorPage listBuyingOrders(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", defaultValue = "20") @Min(1) @Max(50) int limit) {
        return orderService.listBuyingOrders(currentUserResolver.getCurrentActiveUser(), status, cursor, limit);
    }

    @GetMapping("/me/selling")
    public OrderCursorPage listSellingOrders(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", defaultValue = "20") @Min(1) @Max(50) int limit) {
        return orderService.listSellingOrders(currentUserResolver.getCurrentActiveUser(), status, cursor, limit);
    }

    @GetMapping("/{orderId}")
    public OrderDetailResponse getOrderDetail(@PathVariable("orderId") Long orderId) {
        return orderService.getOrderDetail(orderId, currentUserResolver.getCurrentActiveUser());
    }

    @PostMapping("/{orderId}/confirm")
    public OrderDetailResponse confirmOrder(@PathVariable("orderId") Long orderId,
                                            @Valid @RequestBody ConfirmOrderRequest payload) {
        return orderService.confirmOrder(orderId, currentUserResolver.getCurrentActiveUser(), payload);
    }

    @PatchMapping("/{orderId}/transaction-terms")
    public OrderDetailResponse updateTransactionTerms(@PathVariable("orderId") Long orderId,
                                                      @Valid @RequestBody UpdateOrderTransactionTermsRequest payload) {
        return orderService.updateTransactionTerms(orderId, currentUserResolver.getCurrentActiveUser(), payload);
    }

    @PostMapping(path = "/{orderId}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OrderDetailResponse uploadPaymentReceipt(@PathVariable("orderId") Long orderId,
                                                    @RequestPart("file") MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("Không thể đọc tệp biên nhận.");
        }
        return orderService.uploadPaymentReceipt(orderId, currentUserResolver.getCurrentActiveUser(),
                file.getOriginalFilename(), file.getContentType(), bytes);
    }

    @PostMapping("/{orderId}/confirm-payment")
    public OrderDetailResponse confirmPayment(@PathVariable("orderId") Long orderId) {
        return orderService.confirmPayment(orderId, currentUserResolver.getCurrentActiveUser());
    }

    @PostMapping("/{orderId}/deliver")
    public OrderDetailResponse markDelivering(@PathVariable("orderId") Long orderId) {
        return orderService.markDelivering(orderId, currentUserResolver.getCurrentActiveUser());
    }

    @PostMapping("/{orderId}/complete")
    public OrderDetailResponse completeOrder(@PathVariable("orderId") Long orderId) {
        return orderService.completeOrder(orderId, currentUserResolver.getCurrentActiveUser());
    }

    @PostMapping("/{orderId}/cancel")
    public OrderDetailResponse cancelOrder(@PathVariable("orderId") Long orderId,
                                           @Valid @RequestBody CancelOrderRequest payload) {
        return orderService.cancelOrder(orderId, currentUserResolver.getCurrentActiveUser(), payload);
    }
}
