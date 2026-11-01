
package com.ptiters.marketplace.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByProductIdAndDeletedFalse(Long productId);

    List<Product> findBySellerIdAndDeletedFalseOrderByCreatedAtDesc(Long sellerId);

    Page<Product> findByStatusAndModerationStatusAndDeletedFalse(
            String status,
            String moderationStatus,
            Pageable pageable
    );

    boolean existsByProductIdAndSellerIdAndDeletedFalse(
            Long productId,
            Long sellerId
    );
}
