package com.ptiters.marketplace.feedback;

import jakarta.validation.constraints.NotBlank;

public record AdminSupportClaimReject(
    @NotBlank String reason
) {}
