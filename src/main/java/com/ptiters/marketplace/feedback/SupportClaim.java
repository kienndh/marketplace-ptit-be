package com.ptiters.marketplace.feedback;

import com.ptiters.marketplace.user.User;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entity ánh xạ bảng PostgreSQL `support_claims` (Wave 0, khớp schema DB_v7 / Alembic).
 */
@Entity
@Table(name = "support_claims")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "claim_id", nullable = false)
    private Long claimId;

    @Column(name = "supporter_id", nullable = false)
    private Long supporterId;

    @Column(name = "claimed_amount_vnd", nullable = false)
    private Long claimedAmountVnd;

    @Column(name = "claimed_transferred_at", nullable = false)
    private OffsetDateTime claimedTransferredAt;

    @Column(name = "sender_account_name", length = 100)
    private String senderAccountName;

    @Column(name = "wants_public", nullable = false)
    @Builder.Default
    private Boolean wantsPublic = false;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SupportClaimStatus status = SupportClaimStatus.PENDING;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "contribution_id")
    private Long contributionId;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supporter_id", insertable = false, updatable = false)
    private User supporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by", insertable = false, updatable = false)
    private User reviewer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contribution_id", insertable = false, updatable = false)
    private SupportContribution contribution;

    @PrePersist
    protected void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        }
    }

}
