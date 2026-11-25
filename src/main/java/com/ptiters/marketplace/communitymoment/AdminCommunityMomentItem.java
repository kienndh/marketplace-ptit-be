package com.ptiters.marketplace.communitymoment;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record AdminCommunityMomentItem(
    @JsonProperty("moment_id") Long momentId,
    @JsonProperty("image_url") String imageUrl,
    @JsonProperty("thumbnail_url") String thumbnailUrl,
    @JsonProperty("detail_image_url") String detailImageUrl,
    String note,
    String status,
    @JsonProperty("created_at") LocalDateTime createdAt,
    @JsonProperty("reviewed_at") LocalDateTime reviewedAt,
    AdminCommunityMomentUploader uploader,
    AdminCommunityMomentReviewer reviewer
) {}
