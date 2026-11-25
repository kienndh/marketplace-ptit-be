package com.ptiters.marketplace.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDeviceToken {
    private Long id;
    private Long userId;
    private String token;
    private String deviceType;
    private String userAgent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
