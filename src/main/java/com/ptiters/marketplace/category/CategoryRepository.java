package com.ptiters.marketplace.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Integer> {
    List<Category> findByDeletedFalseOrderByCategoryIdAsc();

    Optional<Category> findByCategoryIdAndDeletedFalse(Integer categoryId);
}
