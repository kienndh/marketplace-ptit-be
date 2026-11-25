package com.ptiters.marketplace.notification;

import jakarta.validation.constraints.NotBlank;

public record PushUnsubscribeRequest(
    @NotBlank String token
) {}
