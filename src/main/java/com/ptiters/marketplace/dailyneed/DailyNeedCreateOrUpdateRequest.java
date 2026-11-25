package com.ptiters.marketplace.dailyneed;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DailyNeedCreateOrUpdateRequest(
    @NotBlank(message = "Nội dung nhật ký không được để trống.")
    @Size(max = 120, message = "Nội dung nhật ký tối đa 120 ký tự.")
    String content
) {}
