package com.ptiters.marketplace.feedback;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportClaim {
    private Long id;
    private Long userId;
    private Long contributionId;
    private SupportClaimStatus status;
    private String proofUrl;
    private Long verifiedBy;
    private LocalDateTime verifiedAt;
    private LocalDateTime createdAt;
}
