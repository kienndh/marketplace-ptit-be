package com.ptiters.marketplace.auction;

import com.ptiters.marketplace.order.DateTimes;
import com.ptiters.marketplace.worker.SweepResult;
import com.ptiters.marketplace.worker.WorkerRetryExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Các vòng quét định kỳ của đấu giá (port {@code run_auction_scheduled_sweep}, {@code run_auction_close_sweep},
 * {@code run_winner_timeout_sweep}). Đọc danh sách ứng viên (id), rồi xử lý TỪNG phiên trong transaction riêng
 * (qua {@link AuctionLifecycleService}) bọc {@link WorkerRetryExecutor}. Lỗi một phiên không chặn các phiên khác.
 */
@Component
public class AuctionSweeper {

    private static final Logger log = LoggerFactory.getLogger(AuctionSweeper.class);

    @FunctionalInterface
    private interface CandidateLoader {
        List<Long> load(LocalDateTime now, int limit);
    }

    @FunctionalInterface
    private interface ItemAction {
        boolean apply(Long id, LocalDateTime now) throws Exception;
    }

    private final AuctionQueryRepository auctions;
    private final AuctionLifecycleService lifecycle;
    private final WorkerRetryExecutor retry;

    @Value("${marketplace.worker.batch-size:50}")
    private int batchSize;
    @Value("${marketplace.worker.max-retries:3}")
    private int maxRetries;
    @Value("${marketplace.worker.retry-backoff-ms:500}")
    private long backoffMillis;

    public AuctionSweeper(AuctionQueryRepository auctions, AuctionLifecycleService lifecycle, WorkerRetryExecutor retry) {
        this.auctions = auctions;
        this.lifecycle = lifecycle;
        this.retry = retry;
    }

    /** SCHEDULED đến giờ → ACTIVE (hoặc kết thúc luôn nếu đã quá hạn). */
    public SweepResult runScheduledSweep() {
        return sweep("auction_scheduled_sweep", auctions::findScheduledToSweep,
                lifecycle::activateOrEndSingleScheduledAuction);
    }

    /** ACTIVE hết hạn → ENDED, chọn người thắng, giữ hàng. */
    public SweepResult runCloseSweep() {
        return sweep("auction_close_sweep", auctions::findActiveToClose, lifecycle::closeSingleAuctionIfExpired);
    }

    /** Người thắng quá hạn checkout mà chưa có đơn → trả giữ hàng. */
    public SweepResult runWinnerTimeoutSweep() {
        return sweep("winner_timeout_sweep", auctions::findExpiredWinnerAuctions, lifecycle::timeoutSingleWinnerCheckout);
    }

    private SweepResult sweep(String name, CandidateLoader loader, ItemAction action) {
        LocalDateTime now = DateTimes.nowUtc();
        List<Long> candidates = loader.load(now, batchSize);
        if (candidates.isEmpty()) {
            return SweepResult.empty();
        }
        int changed = 0;
        int noOp = 0;
        int failed = 0;
        for (Long id : candidates) {
            try {
                Callable<Boolean> op = () -> action.apply(id, now);
                Boolean result = retry.executeWithRetry(op, maxRetries, backoffMillis, name + ":" + id);
                if (Boolean.TRUE.equals(result)) {
                    changed++;
                } else {
                    noOp++;
                }
            } catch (Exception e) {
                failed++;
                log.error("[{}] Error processing auction {} ({}): {}", name, id, e.getClass().getSimpleName(), e.getMessage());
            }
        }
        log.info("[{}] completed: candidates={}, changed={}, no_op={}, failed={}", name, candidates.size(), changed, noOp, failed);
        return new SweepResult(changed, candidates.size(), noOp, failed);
    }
}
