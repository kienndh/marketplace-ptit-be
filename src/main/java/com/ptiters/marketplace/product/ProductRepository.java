
package com.ptiters.marketplace.product;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

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


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT p
        FROM Product p
        WHERE p.productId = :productId
          AND p.deleted = false
        """)
    Optional<Product> findByIdForUpdate(
            @Param("productId") Long productId
    );

}
