package com.ptiters.marketplace.worker;

import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;

@Component
public class WorkerRetryExecutor {

    public <T> T executeWithRetry(Callable<T> task, int maxAttempts) throws Exception {
        throw new UnsupportedOperationException("TODO: Implement worker retry mechanism");
    }
}
