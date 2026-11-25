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
public class SiteFeedback {
    private Long id;
    private Long userId;
    private String senderName;
    private String content;
    private Boolean isAnonymous;
    private Boolean isPublic;
    private String adminNotes;
    private LocalDateTime createdAt;
}
