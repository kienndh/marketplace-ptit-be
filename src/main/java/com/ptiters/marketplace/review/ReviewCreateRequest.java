package com.ptiters.marketplace.review;

public record ReviewCreateRequest(
        Integer rating,
        String comment
) {}
