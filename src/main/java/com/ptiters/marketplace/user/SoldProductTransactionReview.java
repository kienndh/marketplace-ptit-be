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
public class SoldProductTransactionReview {

    @JsonProperty("rating")
    private int rating;

    @JsonProperty("comment")
    private String comment;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("verified_transaction")
    private boolean verifiedTransaction = true;
}
