package com.ptiters.marketplace.tutorial;

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
public class Tutorial {
    private Long id;
    private String featureKey;
    private String title;
    private String description;
    private String videoUrl;
    private String videoStorageKey;
    private String videoMimeType;
    private Long videoSizeBytes;
    private Long durationMs;
    private String thumbnailUrl;
    private String actionKey;
    private Integer sortOrder;
    private Boolean isPublished;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
