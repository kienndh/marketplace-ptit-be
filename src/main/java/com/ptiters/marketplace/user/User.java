package com.ptiters.marketplace.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "users")
@NoArgsConstructor
public class User {
    @Id
    @Column(name = "user_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "email", nullable = false, length = 150, unique = true)
    private String email;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "phone", nullable = true, length = 20)
    private String phone;

    @Column(name = "show_phone_publicly", nullable = false)
    private boolean showPhonePublicly = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "social_links", columnDefinition = "json")
    private List<SocialLink> socialLinks;

    @Column(name = "avatar_url", nullable = true, length = 500)
    private String avatarUrl;

    @Column(name = "bio", nullable = true, length = 500)
    private String bio;

    @Column(name = "delivery_area", nullable = true, length = 255)
    private String deliveryArea;

    @Column(name = "default_meetup_area", nullable = true, length = 255)
    private String defaultMeetupArea;

    @Column(name = "bank_bin", nullable = true, length = 10)
    private String bankBin;

    @Column(name = "bank_account_number", nullable = true, length = 30)
    private String bankAccountNumber;

    @Column(name = "bank_account_name", nullable = true, length = 100)
    private String bankAccountName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", nullable = false, length = 10)
    private UserRole role = UserRole.STUDENT;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "user_status")
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.publicId == null) {
            this.publicId = UUID.randomUUID();
        }
    }
}
