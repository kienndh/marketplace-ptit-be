package com.ptiters.marketplace.order;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateOrderRequest(
        @JsonProperty("product_id") Long productId,
        Integer quantity,
        @JsonProperty("contact_name") String contactName,
        @JsonProperty("contact_phone") String contactPhone,
        @JsonProperty("fulfillment_method") String fulfillmentMethod,
        @JsonProperty("meetup_place") String meetupPlace,
        @JsonProperty("shipping_address") String shippingAddress,
        @JsonProperty("payment_method") String paymentMethod,
        @JsonProperty("buyer_note") String buyerNote
) {}
