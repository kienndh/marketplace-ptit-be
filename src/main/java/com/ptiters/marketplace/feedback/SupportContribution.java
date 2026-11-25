package com.ptiters.marketplace.feedback;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportContribution {
    private Long id;
    private Long userId;
    private BigDecimal amount;
    private String donorName;
    private String donorMessage;
    private Boolean isPublic;
    private LocalDateTime donatedAt;
}
