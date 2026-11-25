package com.ptiters.marketplace.worker;

import com.ptiters.marketplace.integration.MediaStorage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Worker xử lý hàng đợi dọn rác media ({@code media_cleanup_jobs}) khớp {@code app/workers/media_cleanup.py}.
 *
 * <p>Chiến lược thực thi an toàn đa worker &amp; không giữ DB lock khi gọi Cloudinary:
 * <ol>
 *   <li>Claim batch bằng {@code SELECT ... FOR UPDATE SKIP LOCKED}, gia hạn {@code next_retry_at} thêm
 *       {@link #CLAIM_LEASE_SECONDS} (300s) rồi commit ngay.</li>
 *   <li>Thực thi {@link MediaStorage#destroyAsset(String, String)} ngoài transaction DB.</li>
 *   <li>Thành công → xoá job; thất bại → tăng {@code attempt_count}, cập nhật {@code next_retry_at} theo
 *       exponential backoff bị chặn trần 24h và ghi {@code last_error} đã làm sạch thông tin nhạy cảm.</li>
 * </ol>
 */
@Component
public class MediaCleanupWorker {

    private static final Logger log = LoggerFactory.getLogger(MediaCleanupWorker.class);

    public static final int INITIAL_RETRY_SECONDS = 60;
    public static final int CLAIM_LEASE_SECONDS = 300;
    public static final long MAX_BACKOFF_SECONDS = 86_400L;
    public static final int MAX_ERROR_LENGTH = 500;
    static final long[] BACKOFF_DELAYS_SECONDS = {60L, 300L, 900L, 3600L, 21600L};

    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            Pattern.compile("(api_key|api_secret|secret|password|token|bearer|authorization)=[^&\\s]+",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Bearer\\s+[A-Za-z0-9\\-._~+/]+=*", Pattern.CASE_INSENSITIVE)
    );

    private final MediaStorage mediaStorage;
    private final WorkerRetryExecutor retryExecutor;
    private final TransactionTemplate requiresNewTx;
    private final int batchSize;

    @PersistenceContext
    private EntityManager entityManager;

    public MediaCleanupWorker(
            MediaStorage mediaStorage,
            WorkerRetryExecutor retryExecutor,
            PlatformTransactionManager transactionManager,
            @Value("${WORKER_BATCH_SIZE:50}") int batchSize) {
        this.mediaStorage = mediaStorage;
        this.retryExecutor = retryExecutor;
        this.batchSize = Math.max(1, batchSize);
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Tương thích signature skeleton: trả về số job đã dọn sạch thành công. */
    public int runSweep() {
        return runCleanupSweep().changed();
    }

    /** Thực thi một chu kỳ quét và xử lý tối đa {@code batchSize} job đến hạn. */
    public SweepResult runCleanupSweep() {
        long startNanos = System.nanoTime();
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);

        List<ClaimedJob> candidates = claimDueJobs(nowUtc);
        if (candidates.isEmpty()) {
            return SweepResult.empty();
        }

        int changedCount = 0;
        int failedCount = 0;

        for (ClaimedJob job : candidates) {
            try {
                boolean destroyed = mediaStorage.destroyAsset(job.storageKey(), job.resourceType());
                if (destroyed) {
                    deleteCompletedJob(job.cleanupId());
                    changedCount++;
                    log.info("[media_cleanup] Deleted asset {} ({}), removed job {}",
                            job.storageKey(), job.resourceType(), job.cleanupId());
                } else {
                    failedCount++;
                    int newAttempts = job.attemptCount() + 1;
                    LocalDateTime nextRetry = calculateNextRetryAt(newAttempts, nowUtc);
                    String errorMsg = "Cloudinary destroy returned non-ok result";
                    recordRetryFailure(job.cleanupId(), newAttempts, nextRetry, errorMsg, nowUtc);
                    log.warn("[media_cleanup] Asset destroy non-ok for {}, scheduled retry {} at {}",
                            job.storageKey(), newAttempts, nextRetry);
                }
            } catch (Exception e) {
                failedCount++;
                int newAttempts = job.attemptCount() + 1;
                LocalDateTime nextRetry = calculateNextRetryAt(newAttempts, nowUtc);
                String sanitizedErr = sanitizeExternalError(e);
                try {
                    recordRetryFailure(job.cleanupId(), newAttempts, nextRetry, sanitizedErr, nowUtc);
                } catch (Exception dbErr) {
                    log.error("[media_cleanup] Failed to update cleanup job {} after error: {}",
                            job.cleanupId(), sanitizeExternalError(dbErr));
                }
                log.warn("[media_cleanup] Exception during destroy for {}: {}. Scheduled retry {} at {}",
                        job.storageKey(), sanitizedErr, newAttempts, nextRetry);
            }
        }

        double elapsedMs = (System.nanoTime() - startNanos) / 1_000_000.0;
        log.info("[run_media_cleanup_sweep] completed in {}ms: candidates={}, changed={}, failed={}",
                String.format("%.1f", elapsedMs), candidates.size(), changedCount, failedCount);
        return new SweepResult(changedCount, candidates.size(), 0, failedCount);
    }

    private List<ClaimedJob> claimDueJobs(LocalDateTime nowUtc) {
        try {
            return retryExecutor.executeWithRetry(() -> requiresNewTx.execute(status -> {
                @SuppressWarnings("unchecked")
                List<MediaCleanupJob> due = entityManager.createNativeQuery(
                                "SELECT * FROM media_cleanup_jobs "
                                + "WHERE next_retry_at <= :nowUtc "
                                + "ORDER BY next_retry_at ASC, cleanup_id ASC "
                                + "LIMIT :batchSize FOR UPDATE SKIP LOCKED",
                                MediaCleanupJob.class)
                        .setParameter("nowUtc", nowUtc)
                        .setParameter("batchSize", batchSize)
                        .getResultList();

                if (due.isEmpty()) {
                    return List.of();
                }

                LocalDateTime leaseExpiry = nowUtc.plusSeconds(CLAIM_LEASE_SECONDS);
                List<ClaimedJob> claimed = new ArrayList<>(due.size());
                for (MediaCleanupJob job : due) {
                    claimed.add(new ClaimedJob(
                            job.getCleanupId(),
                            job.getStorageKey(),
                            job.getResourceType(),
                            job.getAttemptCount() == null ? 0 : job.getAttemptCount()
                    ));
                    job.setNextRetryAt(leaseExpiry);
                    job.setUpdatedAt(nowUtc);
                }
                entityManager.flush();
                return claimed;
            }), "media_cleanup:claim");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to claim media cleanup jobs", e);
        }
    }

    private void deleteCompletedJob(Long cleanupId) throws Exception {
        retryExecutor.executeWithRetry(() -> {
            requiresNewTx.executeWithoutResult(status ->
                    entityManager.createQuery("DELETE FROM MediaCleanupJob j WHERE j.cleanupId = :id")
                            .setParameter("id", cleanupId)
                            .executeUpdate());
            return null;
        }, "media_cleanup:delete:" + cleanupId);
    }

    private void recordRetryFailure(
            Long cleanupId,
            int attemptCount,
            LocalDateTime nextRetryAt,
            String sanitizedError,
            LocalDateTime nowUtc) throws Exception {
        retryExecutor.executeWithRetry(() -> {
            requiresNewTx.executeWithoutResult(status ->
                    entityManager.createQuery(
                                    "UPDATE MediaCleanupJob j "
                                    + "SET j.attemptCount = :attemptCount, "
                                    + "j.nextRetryAt = :nextRetryAt, "
                                    + "j.lastError = :lastError, "
                                    + "j.updatedAt = :updatedAt "
                                    + "WHERE j.cleanupId = :id")
                            .setParameter("attemptCount", attemptCount)
                            .setParameter("nextRetryAt", nextRetryAt)
                            .setParameter("lastError", sanitizedError)
                            .setParameter("updatedAt", nowUtc)
                            .setParameter("id", cleanupId)
                            .executeUpdate());
            return null;
        }, "media_cleanup:retry:" + cleanupId);
    }

    public static LocalDateTime calculateNextRetryAt(int attemptCount, LocalDateTime baseTimeUtc) {
        LocalDateTime base = baseTimeUtc != null ? baseTimeUtc : LocalDateTime.now(ZoneOffset.UTC);
        int idx = Math.max(0, attemptCount - 1);
        long delaySeconds = idx < BACKOFF_DELAYS_SECONDS.length
                ? BACKOFF_DELAYS_SECONDS[idx]
                : MAX_BACKOFF_SECONDS;
        return base.plusSeconds(delaySeconds);
    }

    public static String sanitizeExternalError(Throwable exc) {
        if (exc == null) {
            return "";
        }
        String excType = exc.getClass().getSimpleName();
        String msg = exc.getMessage();
        String raw = (msg != null && !msg.isBlank()) ? excType + ": " + msg : excType;
        return sanitizeExternalError(raw, MAX_ERROR_LENGTH);
    }

    public static String sanitizeExternalError(String rawMessage, int maxLength) {
        String text = rawMessage == null ? "" : rawMessage;
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            text = pattern.matcher(text).replaceAll("[REDACTED]");
        }
        text = text.trim().replaceAll("\\s+", " ");
        if (text.length() > maxLength) {
            int limit = Math.max(0, maxLength - 3);
            return text.substring(0, limit) + "...";
        }
        return text;
    }

    private record ClaimedJob(Long cleanupId, String storageKey, String resourceType, int attemptCount) {
    }
}
