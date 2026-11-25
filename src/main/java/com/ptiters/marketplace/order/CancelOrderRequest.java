package com.ptiters.marketplace.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Python: {@code CancelOrderRequest} — lý do bắt buộc, không rỗng/blank, tối đa 255 ký tự. */
public record CancelOrderRequest(
        @NotBlank @Size(max = 255) @JsonProperty("cancel_reason") String cancelReason
) {
}
