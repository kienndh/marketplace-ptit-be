package com.ptiters.marketplace.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Size;

/**
 * Python: {@code UpdateOrderTransactionTermsRequest}. Cần phân biệt "không gửi" và "gửi null" cho
 * {@code meetup_time}/{@code shipping_expected_at} (Python: {@code model_fields_set}) nên dùng class thay vì record.
 */
public class UpdateOrderTransactionTermsRequest {

    @JsonProperty("fulfillment_method")
    private FulfillmentMethod fulfillmentMethod;

    @JsonProperty("payment_method")
    private PaymentMethod paymentMethod;

    @Size(max = 255)
    @JsonProperty("meetup_place")
    private String meetupPlace;

    private String meetupTime;
    private boolean meetupTimeSet;

    @Size(max = 500)
    @JsonProperty("shipping_address")
    private String shippingAddress;

    private String shippingExpectedAt;
    private boolean shippingExpectedAtSet;

    @Size(max = 255)
    @JsonProperty("payment_note")
    private String paymentNote;

    public FulfillmentMethod getFulfillmentMethod() {
        return fulfillmentMethod;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getMeetupPlace() {
        return meetupPlace;
    }

    public String getMeetupTime() {
        return meetupTime;
    }

    public boolean isMeetupTimeSet() {
        return meetupTimeSet;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public String getShippingExpectedAt() {
        return shippingExpectedAt;
    }

    public boolean isShippingExpectedAtSet() {
        return shippingExpectedAtSet;
    }

    public String getPaymentNote() {
        return paymentNote;
    }

    @JsonSetter("meetup_time")
    public void setMeetupTime(String meetupTime) {
        this.meetupTime = meetupTime;
        this.meetupTimeSet = true;
    }

    @JsonSetter("shipping_expected_at")
    public void setShippingExpectedAt(String shippingExpectedAt) {
        this.shippingExpectedAt = shippingExpectedAt;
        this.shippingExpectedAtSet = true;
    }

    public void setFulfillmentMethod(FulfillmentMethod fulfillmentMethod) {
        this.fulfillmentMethod = fulfillmentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public void setMeetupPlace(String meetupPlace) {
        this.meetupPlace = meetupPlace;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public void setPaymentNote(String paymentNote) {
        this.paymentNote = paymentNote;
    }
}
