package com.ptiters.marketplace.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserAccessStatusResponse {

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("role")
    private String role;

    @JsonProperty("can_buy")
    private boolean canBuy = true;

    @JsonProperty("can_sell")
    private boolean canSell = true;

    @JsonProperty("active_restrictions")
    private List<UserCapabilityRestrictionResponse> activeRestrictions = new ArrayList<>();
}
