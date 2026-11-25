package com.ptiters.marketplace.communitymoment;

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
public class CommunityMoment {
    private Long id;
    private Long uploaderId;
    private String imageUrl;
    private String storageKey;
    private String mimeType;
    private Long sizeBytes;
    private String note;
    private CommunityMomentStatus status;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
