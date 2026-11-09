
package com.ptiters.marketplace.product.image;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductImageRepository
        extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProduct_ProductIdOrderBySortOrderAscImageIdAsc(
            Long productId
    );


    List<ProductImage> findByProduct_ProductIdInAndThumbnailTrue(
            Collection<Long> productIds
    );


    Optional<ProductImage> findByImageIdAndProduct_ProductId(
            Long imageId,
            Long productId
    );

    Optional<ProductImage> findByProduct_ProductIdAndThumbnailTrue(
            Long productId
    );

    boolean existsByProduct_ProductIdAndThumbnailTrue(
            Long productId
    );

    long countByProduct_ProductId(Long productId);
}
