package com.ptiters.marketplace.worker;

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
public class MediaCleanupJob {
    private Long id;
    private String publicId;
    private String resourceType;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private String errorReason;
    private LocalDateTime createdAt;
}
