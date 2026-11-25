package com.ptiters.marketplace.notification;

public record PushSubscriptionResponse(
    boolean success,
    String message
) {}
