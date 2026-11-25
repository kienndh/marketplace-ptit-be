package com.ptiters.marketplace.worker;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Locale;

/**
 * Helper dùng chung cho các track (conversation, communitymoment, tutorial, feedback, product, ...)
 * để ghi và dọn bản ghi vào bảng hàng đợi {@code media_cleanup_jobs}.
 *
 * <p>Hoạt động trong cùng transaction DB của caller (hoặc tạo transaction mới nếu gọi ngoài transaction),
 * sử dụng {@code ON CONFLICT (storage_key, resource_type) DO NOTHING} để chống enqueue trùng lặp.
 */
@Service
public class MediaCleanupScheduler {

    public static final Duration DEFAULT_INITIAL_DELAY = Duration.ofSeconds(MediaCleanupWorker.INITIAL_RETRY_SECONDS);

    @PersistenceContext
    private EntityManager entityManager;

    public record CleanupTarget(String storageKey, String resourceType) {
    }

    @Transactional
    public void enqueue(String storageKey, String resourceType) {
        enqueue(storageKey, resourceType, DEFAULT_INITIAL_DELAY);
    }

    @Transactional
    public void enqueue(String storageKey, String resourceType, Duration initialDelay) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        String normalizedKey = storageKey.trim();
        String normalizedType = normalizeResourceType(resourceType);
        Duration safeDelay = (initialDelay == null || initialDelay.isNegative()) ? Duration.ZERO : initialDelay;

        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime nextRetryAt = nowUtc.plus(safeDelay);

        entityManager.createNativeQuery(
                        "INSERT INTO media_cleanup_jobs "
                        + "(storage_key, resource_type, attempt_count, next_retry_at, created_at, updated_at) "
                        + "VALUES (:storageKey, :resourceType, 0, :nextRetryAt, :createdAt, :updatedAt) "
                        + "ON CONFLICT (storage_key, resource_type) DO NOTHING")
                .setParameter("storageKey", normalizedKey)
                .setParameter("resourceType", normalizedType)
                .setParameter("nextRetryAt", nextRetryAt)
                .setParameter("createdAt", nowUtc)
                .setParameter("updatedAt", nowUtc)
                .executeUpdate();
    }

    @Transactional
    public void enqueueAll(Collection<CleanupTarget> targets) {
        enqueueAll(targets, DEFAULT_INITIAL_DELAY);
    }

    @Transactional
    public void enqueueAll(Collection<CleanupTarget> targets, Duration initialDelay) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        for (CleanupTarget target : targets) {
            if (target != null) {
                enqueue(target.storageKey(), target.resourceType(), initialDelay);
            }
        }
    }

    @Transactional
    public void deleteJob(String storageKey, String resourceType) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        entityManager.createQuery(
                        "DELETE FROM MediaCleanupJob j "
                        + "WHERE j.storageKey = :storageKey AND j.resourceType = :resourceType")
                .setParameter("storageKey", storageKey.trim())
                .setParameter("resourceType", normalizeResourceType(resourceType))
                .executeUpdate();
    }

    static String normalizeResourceType(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            return "image";
        }
        return resourceType.trim().toLowerCase(Locale.ROOT);
    }
}
