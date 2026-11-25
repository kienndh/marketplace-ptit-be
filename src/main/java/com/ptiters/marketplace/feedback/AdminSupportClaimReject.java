package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

public record AdminSupportClaimReject(
        @Size(max = 255, message = "Lý do từ chối tối đa 255 ký tự.")
        @JsonProperty("rejection_reason") String rejectionReason
) {}
