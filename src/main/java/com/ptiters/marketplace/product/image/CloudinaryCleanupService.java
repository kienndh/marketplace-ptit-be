
package com.ptiters.marketplace.product.image;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryCleanupService {

    private final CloudinaryCleanupTaskRepository cleanupTaskRepository;
    private final Cloudinary cloudinary;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    public void processTask(Long taskId) {

        String publicId = transactionTemplate.execute(status -> {

            CloudinaryCleanupTask task = entityManager.find(
                    CloudinaryCleanupTask.class,
                    taskId,
                    LockModeType.PESSIMISTIC_WRITE
            );

            if (task == null
                    || !"PENDING".equals(task.getStatus())
                    || task.getNextRetryAt().isAfter(LocalDateTime.now())) {
                return null;
            }

            if (task.getRetryCount() >= task.getMaxRetries()) {
                task.setStatus("FAILED");
                return null;
            }

            task.setStatus("PROCESSING");
            task.setRetryCount(task.getRetryCount() + 1);

            return task.getPublicId();
        });

        if (publicId == null) {
            return;
        }

        try {
            Map<?, ?> result = cloudinary.uploader().destroy(
                    publicId,
                    ObjectUtils.asMap("resource_type", "image")
            );

            Object value = result.get("result");

            if (!"ok".equals(value) && !"not found".equals(value)) {
                throw new IllegalStateException(
                        "Cloudinary delete returned: " + value
                );
            }

            transactionTemplate.executeWithoutResult(status -> {

                CloudinaryCleanupTask task =
                        cleanupTaskRepository.findById(taskId)
                                .orElseThrow();

                if (!"PROCESSING".equals(task.getStatus())) {
                    return;
                }

                task.setStatus("COMPLETED");
                task.setLastError(null);
            });

            log.info("Cloudinary cleanup completed: {}", publicId);

        } catch (Exception e) {

            log.error(
                    "Cloudinary cleanup failed: {}",
                    publicId,
                    e
            );

            transactionTemplate.executeWithoutResult(status -> {

                CloudinaryCleanupTask task =
                        cleanupTaskRepository.findById(taskId)
                                .orElseThrow();

                if (!"PROCESSING".equals(task.getStatus())) {
                    return;
                }

                String message = e.getMessage() == null
                        ? e.getClass().getSimpleName()
                        : e.getMessage();

                task.setLastError(
                        message.length() > 2000
                                ? message.substring(0, 2000)
                                : message
                );

                if (task.getRetryCount() >= task.getMaxRetries()) {
                    task.setStatus("FAILED");
                } else {
                    task.setStatus("PENDING");

                    long delay = 1L << Math.min(
                            task.getRetryCount() - 1, 10
                    );

                    task.setNextRetryAt(
                            LocalDateTime.now().plusMinutes(delay)
                    );
                }
            });
        }
    }


    public void recoverStuckTasks() {

        LocalDateTime expiredAt =
                LocalDateTime.now().minusMinutes(10);

        List<CloudinaryCleanupTask> tasks =
                cleanupTaskRepository
                        .findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                                "PROCESSING",
                                expiredAt,
                                PageRequest.of(0, 20)
                        );

        for (CloudinaryCleanupTask item : tasks) {
            transactionTemplate.executeWithoutResult(status -> {

                CloudinaryCleanupTask task = entityManager.find(
                        CloudinaryCleanupTask.class,
                        item.getTaskId(),
                        LockModeType.PESSIMISTIC_WRITE
                );

                if (task == null
                        || !"PROCESSING".equals(task.getStatus())
                        || !task.getUpdatedAt().isBefore(expiredAt)) {
                    return;
                }

                if (task.getRetryCount() >= task.getMaxRetries()) {
                    task.setStatus("FAILED");
                } else {
                    task.setStatus("PENDING");
                    task.setNextRetryAt(LocalDateTime.now());
                }

                log.warn(
                        "Recovered stuck cleanup task {}, new status: {}",
                        task.getTaskId(),
                        task.getStatus()
                );
            });
        }
    }

}
