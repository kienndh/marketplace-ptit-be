package com.ptiters.marketplace.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Python: {@code CreateOrderRequest}. {@code meetup_time}/{@code shipping_expected_at} nhận chuỗi ISO-8601 (có/không offset),
 * được chuẩn hoá về UTC naive trong service. Quy tắc chéo (địa điểm/địa chỉ theo hình thức giao nhận) kiểm trong service.
 */
public record CreateOrderRequest(
        @NotNull @JsonProperty("product_id") Long productId,
        @Min(1) Integer quantity,
        @JsonProperty("payment_method") PaymentMethod paymentMethod,
        @JsonProperty("fulfillment_method") FulfillmentMethod fulfillmentMethod,
        @Size(max = 255) @JsonProperty("meetup_place") String meetupPlace,
        @JsonProperty("meetup_time") String meetupTime,
        @Size(max = 500) @JsonProperty("shipping_address") String shippingAddress,
        @JsonProperty("shipping_expected_at") String shippingExpectedAt,
        @NotBlank @Size(max = 100) @JsonProperty("contact_name") String contactName,
        @NotBlank @Size(max = 20) @JsonProperty("contact_phone") String contactPhone,
        @Size(max = 500) @JsonProperty("buyer_note") String buyerNote
) {
    public int quantityOrDefault() {
        return quantity == null ? 1 : quantity;
    }

    public FulfillmentMethod fulfillmentOrDefault() {
        return fulfillmentMethod == null ? FulfillmentMethod.MEETUP : fulfillmentMethod;
    }
}
