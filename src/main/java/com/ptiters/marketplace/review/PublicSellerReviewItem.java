package com.ptiters.marketplace.review;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/** Ẩn danh tuyệt đối (API_CONTRACT 8.4): không có tên/avatar/id người mua, không order_id. */
public record PublicSellerReviewItem(
        Integer rating,
        String comment,
        @JsonProperty("verified_transaction") boolean verifiedTransaction,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("updated_at") LocalDateTime updatedAt
) {}
