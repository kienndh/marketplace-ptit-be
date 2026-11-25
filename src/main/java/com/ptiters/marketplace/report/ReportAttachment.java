package com.ptiters.marketplace.report;

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
public class ReportAttachment {
    private Long id;
    private Long reportId;
    private String fileUrl;
    private String fileType;
    private Long fileSize;
    private LocalDateTime createdAt;
}
