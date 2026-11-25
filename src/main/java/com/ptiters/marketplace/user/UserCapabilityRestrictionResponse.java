package com.ptiters.marketplace.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserCapabilityRestrictionResponse {

    @JsonProperty("capability")
    private String capability;

    @JsonProperty("expires_at")
    private LocalDateTime expiresAt;
}
