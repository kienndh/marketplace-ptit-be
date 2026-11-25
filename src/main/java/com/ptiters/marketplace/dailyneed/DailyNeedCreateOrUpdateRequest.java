package com.ptiters.marketplace.dailyneed;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DailyNeedCreateOrUpdateRequest(
    @NotBlank @Size(max = 120) String content
) {}
