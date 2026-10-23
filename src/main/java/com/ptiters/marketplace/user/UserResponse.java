package com.ptiters.marketplace.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class UserResponse {
    @JsonProperty("user_id")
    private Long userId;
    private String email;
    @JsonProperty("full_name")
    private String fullName;
    private String phone;
    @JsonProperty("avatar_url")
    private String avatarUrl;
    private String bio;
    @JsonProperty("delivery_area")
    private String deliveryArea;
    @JsonProperty("default_meetup_area")
    private String defaultMeetupArea;
    @JsonProperty("user_role")
    private UserRole userRole;
    @JsonProperty("user_status")
    private UserStatus userStatus;
    @JsonProperty("last_active_at")
    private LocalDateTime lastActiveAt;
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    @JsonProperty("update_at")
    private LocalDateTime updatedAt;
}
