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
public class SiteFeedbackAttachment {
    private Long id;
    private Long feedbackId;
    private String fileUrl;
    private String fileType;
    private Long fileSize;
    private LocalDateTime createdAt;
}
