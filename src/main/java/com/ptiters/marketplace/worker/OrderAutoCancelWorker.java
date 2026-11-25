package com.ptiters.marketplace.worker;

import com.ptiters.marketplace.auction.AuctionSweeper;
import com.ptiters.marketplace.order.DateTimes;
import com.ptiters.marketplace.order.OrderQueryRepository;
import com.ptiters.marketplace.order.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Worker đơn hàng + đấu giá (port {@code app/workers/order_auto_cancel.py}).
 *
 * <p>Một lượt: (1) tự huỷ đơn PENDING quá {@code auto_cancel_at} (hoàn kho), (2) kích hoạt phiên SCHEDULED, (3) đóng phiên
 * ACTIVE hết hạn, (4) nhả giữ hàng khi người thắng quá hạn checkout. Mỗi bản ghi chạy trong transaction riêng, bọc
 * {@link WorkerRetryExecutor}.
 *
 * <p>Scheduler MẶC ĐỊNH TẮT: chỉ chạy định kỳ khi {@code marketplace.worker.order-auction.enabled=true}. Các hàm
 * {@code run*Sweep()} luôn gọi được trực tiếp (test/điều khiển thủ công).
 */
@Component
public class OrderAutoCancelWorker {

    private static final Logger log = LoggerFactory.getLogger(OrderAutoCancelWorker.class);

    /** Chỉ bật cơ chế @Scheduled khi property bật (không chạm application*.yml). */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "marketplace.worker.order-auction", name = "enabled", havingValue = "true")
    static class SchedulingConfiguration {
    }

    private final OrderQueryRepository orders;
    private final OrderService orderService;
    private final AuctionSweeper auctionSweeper;
    private final WorkerRetryExecutor retry;

    @Value("${marketplace.worker.order-auction.enabled:false}")
    private boolean enabled;
    @Value("${marketplace.worker.batch-size:50}")
    private int batchSize;
    @Value("${marketplace.worker.max-retries:3}")
    private int maxRetries;
    @Value("${marketplace.worker.retry-backoff-ms:500}")
    private long backoffMillis;

    public OrderAutoCancelWorker(OrderQueryRepository orders, OrderService orderService, AuctionSweeper auctionSweeper,
                                 WorkerRetryExecutor retry) {
        this.orders = orders;
        this.orderService = orderService;
        this.auctionSweeper = auctionSweeper;
        this.retry = retry;
    }

    /** Tự huỷ các đơn PENDING quá hạn xác nhận. */
    public SweepResult runSweep() {
        LocalDateTime now = DateTimes.nowUtc();
        List<Long> candidates = orders.findExpiredPendingOrderIds(now, batchSize);
        if (candidates.isEmpty()) {
            return SweepResult.empty();
        }
        int changed = 0;
        int noOp = 0;
        int failed = 0;
        for (Long orderId : candidates) {
            try {
                Callable<Boolean> op = () -> orderService.autoCancelSingleOrder(orderId);
                Boolean cancelled = retry.executeWithRetry(op, maxRetries, backoffMillis, "order_auto_cancel:" + orderId);
                if (Boolean.TRUE.equals(cancelled)) {
                    changed++;
                    log.info("Auto-cancelled expired order {}", orderId);
                } else {
                    noOp++;
                }
            } catch (Exception e) {
                failed++;
                log.error("[run_auto_cancel_sweep] Error auto-cancelling order {} ({}): {}", orderId,
                        e.getClass().getSimpleName(), e.getMessage());
            }
        }
        log.info("[run_auto_cancel_sweep] candidates={}, changed={}, no_op={}, failed={}", candidates.size(), changed, noOp, failed);
        return new SweepResult(changed, candidates.size(), noOp, failed);
    }

    /** Một chu kỳ đầy đủ; lỗi một bước không chặn các bước sau. */
    public void runAllSweeps() {
        runStep("orders auto-cancel", this::runSweep);
        runStep("auctions scheduled", auctionSweeper::runScheduledSweep);
        runStep("auctions close", auctionSweeper::runCloseSweep);
        runStep("auctions winner timeout", auctionSweeper::runWinnerTimeoutSweep);
    }

    @Scheduled(fixedDelayString = "${marketplace.worker.poll-interval-ms:30000}",
            initialDelayString = "${marketplace.worker.initial-delay-ms:30000}")
    public void scheduledTick() {
        if (!enabled) {
            return;
        }
        runAllSweeps();
    }

    private void runStep(String name, java.util.function.Supplier<SweepResult> step) {
        try {
            SweepResult r = step.get();
            if (r.changed() > 0) {
                log.info("Sweep '{}' finished: changed {}", name, r.changed());
            }
        } catch (RuntimeException e) {
            log.error("Unexpected error during sweep '{}': {}", name, e.toString());
        }
    }
}
