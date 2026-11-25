package com.ptiters.marketplace.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductProductIdOrderBySortOrderAscImageIdAsc(Long productId);

    Optional<ProductImage> findByImageIdAndProductProductId(Long imageId, Long productId);

    long countByProductProductId(Long productId);

    Optional<ProductImage> findByProductProductIdAndIsThumbnailTrue(Long productId);

    @org.springframework.data.jpa.repository.Query("select coalesce(max(i.sortOrder), -1) from ProductImage i where i.product.productId = :productId")
    int findMaxSortOrder(@org.springframework.data.repository.query.Param("productId") Long productId);
}
