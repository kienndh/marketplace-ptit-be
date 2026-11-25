package com.ptiters.marketplace.conversation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GroupInviteCreateRequest(
        @JsonProperty("invitee_ids") List<Long> inviteeIds
) {}
