package com.ptiters.marketplace.tutorial;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TutorialUploadSignatureResponse(
    @JsonProperty("cloud_name") String cloudName,
    @JsonProperty("api_key") String apiKey,
    long timestamp,
    String signature,
    String folder,
    @JsonProperty("public_id") String publicId,
    String overwrite,
    @JsonProperty("upload_session_id") String uploadSessionId
) {}
