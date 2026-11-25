package com.ptiters.marketplace.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record PushSubscribeRequest(
    @NotBlank String token,
    @JsonProperty("device_type") String deviceType,
    @JsonProperty("user_agent") String userAgent
) {}
