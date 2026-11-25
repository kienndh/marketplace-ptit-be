package com.ptiters.marketplace.dailyneed;

import com.ptiters.marketplace.user.User;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.*;

/**
 * Entity ánh xạ bảng PostgreSQL `daily_needs` (Wave 0, khớp schema DB_v7 / Alembic).
 */
@Entity
@Table(name = "daily_needs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyNeed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_need_id", nullable = false)
    private Long dailyNeedId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "note_date", nullable = false)
    private LocalDate noteDate;

    @Column(name = "content", nullable = false, length = 120)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

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
