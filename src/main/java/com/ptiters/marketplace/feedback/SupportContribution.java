package com.ptiters.marketplace.feedback;

import com.ptiters.marketplace.user.User;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.*;

/**
 * Entity ánh xạ bảng PostgreSQL `support_contributions` (Wave 0, khớp schema DB_v7 / Alembic).
 */
@Entity
@Table(name = "support_contributions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportContribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contribution_id", nullable = false)
    private Long contributionId;

    @Column(name = "supporter_id", nullable = false)
    private Long supporterId;

    @Column(name = "amount_vnd", nullable = false)
    private Long amountVnd;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private Boolean isPublic = false;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    @Column(name = "transaction_ref", length = 100)
    private String transactionRef;

    @Column(name = "admin_note", length = 255)
    private String adminNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supporter_id", insertable = false, updatable = false)
    private User supporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by", insertable = false, updatable = false)
    private User recorder;

    @PrePersist
    protected void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        }
    }

}
