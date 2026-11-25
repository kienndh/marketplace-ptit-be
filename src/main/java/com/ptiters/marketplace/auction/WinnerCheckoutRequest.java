package com.ptiters.marketplace.auction;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ptiters.marketplace.order.FulfillmentMethod;
import com.ptiters.marketplace.order.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Python: {@code WinnerCheckoutRequest}. {@code meetup_time} là chuỗi ISO-8601 (có/không offset). */
public record WinnerCheckoutRequest(
        @NotNull @JsonProperty("payment_method") PaymentMethod paymentMethod,
        @NotNull @JsonProperty("fulfillment_method") FulfillmentMethod fulfillmentMethod,
        @Size(max = 255) @JsonProperty("meetup_place") String meetupPlace,
        @JsonProperty("meetup_time") String meetupTime,
        @Size(max = 500) @JsonProperty("shipping_address") String shippingAddress,
        @NotBlank @Size(max = 100) @JsonProperty("contact_name") String contactName,
        @NotBlank @Size(max = 20) @JsonProperty("contact_phone") String contactPhone,
        @Size(max = 500) @JsonProperty("buyer_note") String buyerNote
) {
}
