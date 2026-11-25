package com.ptiters.marketplace.notification;

import com.ptiters.marketplace.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * Entity ánh xạ bảng PostgreSQL `user_device_tokens` (Wave 0, khớp schema DB_v7 / Alembic).
 */
// CHƯA gắn @Entity: bảng `user_device_tokens` chưa tồn tại trong marketplace_java_dev (xem docs/PARALLEL_PLAN.md).
// Khi dev DB được nâng lên Alembic c9d0e1f2a3b4, thêm @Entity vào đây.
@Table(name = "user_device_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_token_id", nullable = false)
    private Long deviceTokenId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "fcm_token", nullable = false, columnDefinition = "text")
    private String fcmToken;

    @Column(name = "device_info", length = 255)
    private String deviceInfo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @PrePersist
    protected void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
        }
        if (this.lastSeenAt == null) {
            this.lastSeenAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
        }
    }

}
