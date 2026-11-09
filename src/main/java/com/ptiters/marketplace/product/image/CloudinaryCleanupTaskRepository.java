
package com.ptiters.marketplace.product.image;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CloudinaryCleanupTaskRepository
        extends JpaRepository<CloudinaryCleanupTask, Long> {

    Optional<CloudinaryCleanupTask> findByPublicId(String publicId);

    List<CloudinaryCleanupTask>
    findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            String status,
            LocalDateTime now,
            Pageable pageable
    );


    List<CloudinaryCleanupTask>
    findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            String status,
            LocalDateTime expiredAt,
            Pageable pageable
    );

}
