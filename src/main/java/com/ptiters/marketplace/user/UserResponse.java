package com.ptiters.marketplace.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("public_id")
    private UUID publicId;

    @JsonProperty("email")
    private String email;

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("show_phone_publicly")
    private boolean showPhonePublicly;

    @JsonProperty("social_links")
    private List<SocialLink> socialLinks;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    @JsonProperty("bio")
    private String bio;

    @JsonProperty("delivery_area")
    private String deliveryArea;

    @JsonProperty("default_meetup_area")
    private String defaultMeetupArea;

    @JsonProperty("role")
    private String role;

    @JsonProperty("status")
    private String status;

    @JsonProperty("last_active_at")
    private LocalDateTime lastActiveAt;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    public static UserResponse from(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
                user.getUserId(),
                user.getPublicId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.isShowPhonePublicly(),
                user.getSocialLinks(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getDeliveryArea(),
                user.getDefaultMeetupArea(),
                user.getRole() != null ? user.getRole().name() : null,
                user.getStatus() != null ? user.getStatus().name() : null,
                user.getLastActiveAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
