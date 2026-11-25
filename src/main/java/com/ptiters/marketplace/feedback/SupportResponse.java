package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SupportResponse(
        CreatorPublicInfo creator,
        @JsonProperty("profile_path") String profilePath,
        @JsonProperty("fixed_qr_url") String fixedQrUrl,
        @JsonProperty("support_message") String supportMessage
) {}
