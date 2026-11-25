package com.ptiters.marketplace.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product>, ProductRepositoryCustom {

    Optional<Product> findByProductIdAndIsDeletedFalse(Long productId);

    @Query("SELECT p FROM Product p " +
            "LEFT JOIN FETCH p.images " +
            "LEFT JOIN FETCH p.seller " +
            "LEFT JOIN FETCH p.category " +
            "WHERE p.productId = :id AND p.isDeleted = false")
    Optional<Product> findDetailById(@Param("id") Long id);

    @Query("SELECT p FROM Product p " +
            "LEFT JOIN FETCH p.images " +
            "LEFT JOIN FETCH p.seller " +
            "LEFT JOIN FETCH p.category " +
            "WHERE p.productId = :id")
    Optional<Product> findDetailByIdIncludeDeleted(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Product p SET p.viewCount = GREATEST(p.viewCount, :newCount) WHERE p.productId = :productId")
    int updateViewCountFloor(@Param("productId") Long productId, @Param("newCount") Integer newCount);
}
