package com.ptiters.marketplace.order;

import org.springframework.stereotype.Service;

@Service
public class OrderService {

    public OrderDetailResponse createDirectOrder(Object buyer, CreateOrderRequest payload) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public OrderCursorPage listBuyingOrders(Object buyer, String statusFilter, String cursor, int limit) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public OrderCursorPage listSellingOrders(Object seller, String statusFilter, String cursor, int limit) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public OrderDetailResponse getOrderDetail(Long orderId, Object currentUser) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public OrderDetailResponse updateTerms(Long orderId, Object currentUser, UpdateOrderTransactionTermsRequest payload) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public OrderDetailResponse confirmOrder(Long orderId, Object seller, ConfirmOrderRequest payload) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public OrderDetailResponse cancelOrder(Long orderId, Object currentUser, CancelOrderRequest payload) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }
}
