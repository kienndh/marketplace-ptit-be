package com.ptiters.marketplace.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_capability_restrictions")
@Getter
@Setter
@NoArgsConstructor
public class UserCapabilityRestriction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "restriction_id", nullable = false)
    private Long restrictionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "source_report_id", nullable = false)
    private Long sourceReportId;

    @Column(name = "imposed_by", nullable = false)
    private Long imposedBy;

    @Column(name = "capability", nullable = false, length = 10)
    private String capability;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by")
    private Long revokedBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
