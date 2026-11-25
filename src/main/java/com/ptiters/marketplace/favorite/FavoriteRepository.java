package com.ptiters.marketplace.favorite;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, FavoriteId>, FavoriteRepositoryCustom {

    Optional<Favorite> findByUserIdAndProductId(Long userId, Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    @Modifying
    @Query("DELETE FROM Favorite f WHERE f.userId = :userId AND f.productId = :productId")
    void deleteByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);

    @Query("SELECT f.productId FROM Favorite f JOIN f.product p " +
            "WHERE f.userId = :userId " +
            "AND p.isDeleted = false " +
            "AND p.status = com.ptiters.marketplace.product.ProductStatus.AVAILABLE " +
            "AND p.approvalStatus = com.ptiters.marketplace.product.ProductApprovalStatus.APPROVED " +
            "AND p.moderationStatus = com.ptiters.marketplace.product.ProductModerationStatus.CLEAR " +
            "ORDER BY f.productId ASC")
    List<Long> getVisibleFavoriteIds(@Param("userId") Long userId);
}
