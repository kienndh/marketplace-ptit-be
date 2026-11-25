package com.ptiters.marketplace.worker;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * Entity ánh xạ bảng PostgreSQL `media_cleanup_jobs` (Wave 0, khớp schema DB_v7 / Alembic).
 */
@Entity
@Table(name = "media_cleanup_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaCleanupJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cleanup_id", nullable = false)
    private Long cleanupId;

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Column(name = "resource_type", nullable = false, length = 20)
    private String resourceType;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
        }
        if (this.nextRetryAt == null) {
            this.nextRetryAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
        }
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
        }
    }

    @PreUpdate
    protected void preUpdate() {
        this.updatedAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    }

}
