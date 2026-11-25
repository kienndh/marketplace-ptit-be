package com.ptiters.marketplace.review;

public record ReviewUpdateRequest(
        Integer rating,
        String comment
) {}
