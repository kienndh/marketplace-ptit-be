package com.ptiters.marketplace.auction;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WinnerCheckoutRequest(
        @JsonProperty("contact_name") String contactName,
        @JsonProperty("contact_phone") String contactPhone,
        @JsonProperty("delivery_area") String deliveryArea,
        @JsonProperty("payment_method") String paymentMethod,
        @JsonProperty("fulfillment_method") String fulfillmentMethod,
        @JsonProperty("buyer_note") String buyerNote
) {}
