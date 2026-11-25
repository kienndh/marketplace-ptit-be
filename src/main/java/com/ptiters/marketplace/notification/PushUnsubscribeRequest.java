package com.ptiters.marketplace.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record PushUnsubscribeRequest(
        @NotBlank(message = "FCM token không được để trống.")
        @JsonProperty("fcm_token") String fcmToken
) {}
