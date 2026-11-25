package com.ptiters.marketplace.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Kích hoạt lịch chạy nền {@link MediaCleanupWorker} CHỈ KHI được cấu hình tường minh
 * ({@code MEDIA_CLEANUP_ENABLED=true} hoặc {@code marketplace.workers.media-cleanup.enabled=true}).
 * Mặc định TẮT để các bộ test và instance API không tự ý quét DB.
 */
@Configuration
@EnableScheduling
@ConditionalOnExpression(
        "'${MEDIA_CLEANUP_ENABLED:false}'.equalsIgnoreCase('true') "
        + "|| '${marketplace.workers.media-cleanup.enabled:false}'.equalsIgnoreCase('true')"
)
public class MediaCleanupSchedulingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MediaCleanupSchedulingConfiguration.class);

    private final MediaCleanupWorker mediaCleanupWorker;

    public MediaCleanupSchedulingConfiguration(MediaCleanupWorker mediaCleanupWorker) {
        this.mediaCleanupWorker = mediaCleanupWorker;
    }

    @Scheduled(fixedDelayString = "${WORKER_POLL_INTERVAL_MS:30000}")
    public void scheduledSweep() {
        try {
            SweepResult result = mediaCleanupWorker.runCleanupSweep();
            if (result.changed() > 0) {
                log.info("Media cleanup sweep finished: processed {} assets.", result.changed());
            }
        } catch (Exception e) {
            if (WorkerRetryExecutor.isTransientDbError(e)) {
                log.warn("Database unavailable during scheduled media cleanup sweep: {}", e.getMessage());
            } else {
                log.error("Unexpected error during scheduled media cleanup sweep", e);
            }
        }
    }
}
