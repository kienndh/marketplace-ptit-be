package com.ptiters.marketplace.dailyneed;

import com.ptiters.marketplace.user.User;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.*;

/**
 * Entity ánh xạ bảng PostgreSQL `daily_journal_checkins` (Wave 0, khớp schema DB_v7 / Alembic).
 */
@Entity
@Table(name = "daily_journal_checkins")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyJournalCheckin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "checkin_id", nullable = false)
    private Long checkinId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "checkin_date", nullable = false)
    private LocalDate checkinDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @PrePersist
    protected void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        }
    }

}
