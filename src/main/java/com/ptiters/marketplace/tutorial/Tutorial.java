package com.ptiters.marketplace.tutorial;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.*;

/**
 * Entity ánh xạ bảng PostgreSQL `tutorials` (Wave 0, khớp schema DB_v7 / Alembic).
 */
@Entity
@Table(name = "tutorials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tutorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tutorial_id", nullable = false)
    private Long tutorialId;

    @Column(name = "feature_key", nullable = false, length = 40)
    private String featureKey;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "video_url", nullable = false, length = 500)
    private String videoUrl;

    @Column(name = "video_storage_key", nullable = false, length = 255)
    private String videoStorageKey;

    @Column(name = "video_mime_type", nullable = false, length = 50)
    private String videoMimeType;

    @Column(name = "video_size_bytes", nullable = false)
    private Long videoSizeBytes;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "action_key", length = 50)
    private String actionKey;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_published", nullable = false)
    @Builder.Default
    private Boolean isPublished = false;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        }
        if (this.updatedAt == null) {
            this.updatedAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        }
    }

    @PreUpdate
    protected void preUpdate() {
        this.updatedAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
    }

}
