package com.ptiters.marketplace.conversation;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MessageReactionSetRequest(
        @JsonProperty("reaction_key") ReactionKey reactionKey
) {}
