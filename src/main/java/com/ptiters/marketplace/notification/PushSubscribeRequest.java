package com.ptiters.marketplace.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushSubscribeRequest(
        @NotBlank(message = "FCM token không được để trống.")
        @JsonProperty("fcm_token") String fcmToken,
        @Size(max = 255, message = "Thông tin thiết bị tối đa 255 ký tự.")
        @JsonProperty("device_info") String deviceInfo
) {}
