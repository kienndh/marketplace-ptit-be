package com.ptiters.marketplace.product;

public record AdminApprovalDecisionRequest(
        String decision,
        String note
) {}
