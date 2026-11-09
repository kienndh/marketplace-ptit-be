
package com.ptiters.marketplace.product.image;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CloudinaryCleanupScheduler {

    private final CloudinaryCleanupTaskRepository cleanupTaskRepository;
    private final CloudinaryCleanupService cleanupService;

    @Scheduled(fixedDelay = 30000)
    public void processPendingTasks() {

        List<CloudinaryCleanupTask> tasks =
                cleanupTaskRepository
                        .findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                                "PENDING",
                                LocalDateTime.now(),
                                PageRequest.of(0, 20)
                        );

        if (tasks.isEmpty()) {
            return;
        }

        log.info(
                "Found {} pending Cloudinary cleanup tasks",
                tasks.size()
        );

        for (CloudinaryCleanupTask task : tasks) {
            try {
                cleanupService.processTask(task.getTaskId());
            } catch (Exception e) {
                log.error(
                        "Unexpected error processing cleanup task {}",
                        task.getTaskId(),
                        e
                );
            }
        }
    }
}
