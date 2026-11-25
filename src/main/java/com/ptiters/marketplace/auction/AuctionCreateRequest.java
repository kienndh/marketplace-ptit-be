package com.ptiters.marketplace.auction;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Python: {@code AuctionCreateRequest}. Ưu tiên {@code duration_minutes} (bắt đầu ngay); legacy dùng
 * {@code starts_at}/{@code ends_at} (chuỗi ISO-8601 có/không offset, chuẩn hoá UTC naive trong service).
 */
public record AuctionCreateRequest(
        @NotNull @JsonProperty("product_id") Long productId,
        @NotNull @DecimalMin(value = "0", inclusive = false) @JsonProperty("bid_step") BigDecimal bidStep,
        @Positive @JsonProperty("duration_minutes") Integer durationMinutes,
        @JsonProperty("starts_at") String startsAt,
        @JsonProperty("ends_at") String endsAt
) {
}
