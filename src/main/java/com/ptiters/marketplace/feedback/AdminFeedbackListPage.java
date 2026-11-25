package com.ptiters.marketplace.feedback;

import java.util.List;

public record AdminFeedbackListPage(
    List<AdminFeedbackDetail> items,
    long total,
    int page,
    int size
) {}
