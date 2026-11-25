package com.ptiters.marketplace.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record SupporterPublicIdentity(
        @JsonProperty("public_id") UUID publicId,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("profile_path") String profilePath
) {}
