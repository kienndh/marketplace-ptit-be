package com.ptiters.marketplace.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record UpdateOrderTransactionTermsRequest(
        @JsonProperty("fulfillment_method") String fulfillmentMethod,
        @JsonProperty("meetup_place") String meetupPlace,
        @JsonProperty("meetup_time") LocalDateTime meetupTime,
        @JsonProperty("shipping_address") String shippingAddress,
        @JsonProperty("contact_name") String contactName,
        @JsonProperty("contact_phone") String contactPhone,
        @JsonProperty("buyer_note") String buyerNote
) {}
