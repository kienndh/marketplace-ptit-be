package com.ptiters.marketplace.category;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CategoryWriteIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @BeforeEach
    void verifyDatabaseSafetyBoundary() throws SQLException {
        // Assert the connected database is strictly marketplace_java_test
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT current_database()")) {
            assertTrue(rs.next(), "Database query failed to return current database name");
            String dbName = rs.getString(1);
            org.junit.jupiter.api.Assertions.assertTrue(dbName.matches("marketplace_java_test(_[a-z0-9]+)?"),
                    "SAFETY VIOLATION: Category write tests are strictly forbidden outside marketplace_java_test!");
        }

        assertEquals("false", environment.getProperty("spring.flyway.enabled"),
                "Flyway must be disabled during write tests");
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"),
                "Hibernate ddl-auto must remain validate");
    }

    private Category createSyntheticCategory(String namePrefix, boolean isDeleted) {
        Category category = new Category();
        category.setName(namePrefix + " Category");
        category.setSlug(namePrefix.toLowerCase().replace(" ", "-") + "-" + UUID.randomUUID());
        category.setDeleted(isDeleted);
        return category;
    }

    @Test
    @DisplayName("Verify Category INSERT generates category_id and returns ID to Java entity AS-IS")
    void testCategoryInsertGeneratesIdWithExistingMapping() {
        Category category = createSyntheticCategory("Books", false);
        assertNull(category.getCategoryId(), "ID should be null before insert");

        Category savedCategory = categoryRepository.save(category);
        entityManager.flush();

        // Verify that GenerationType.IDENTITY returned the generated ID from PostgreSQL sequence
        assertNotNull(savedCategory.getCategoryId(), "category_id must be populated after persist and flush");
        assertTrue(savedCategory.getCategoryId() > 0, "category_id must be a positive integer");

        Integer generatedId = savedCategory.getCategoryId();

        entityManager.clear();
        Optional<Category> reloadedOpt = categoryRepository.findById(generatedId);
        assertTrue(reloadedOpt.isPresent(), "Persisted category must be reloadable by generated ID");
        assertEquals(category.getName(), reloadedOpt.get().getName());
        assertEquals(category.getSlug(), reloadedOpt.get().getSlug());
        assertFalse(reloadedOpt.get().isDeleted());
    }

    @Test
    @DisplayName("Verify CategoryRepository queries and soft-deletion exclusion filter")
    void testSoftDeletionExclusionInRepositoryQueries() {
        Category activeCat = createSyntheticCategory("Active Items", false);
        Category deletedCat = createSyntheticCategory("Deleted Items", true);

        categoryRepository.save(activeCat);
        categoryRepository.save(deletedCat);
        entityManager.flush();
        entityManager.clear();

        // 1. findByDeletedFalseOrderByCategoryIdAsc should include activeCat and exclude deletedCat
        List<Category> activeList = categoryRepository.findByDeletedFalseOrderByCategoryIdAsc();
        assertTrue(activeList.stream().anyMatch(c -> c.getCategoryId().equals(activeCat.getCategoryId())),
                "Active category must be present in findByDeletedFalse list");
        assertFalse(activeList.stream().anyMatch(c -> c.getCategoryId().equals(deletedCat.getCategoryId())),
                "Soft-deleted category must be excluded from findByDeletedFalse list");

        // 2. findByCategoryIdAndDeletedFalse should return activeCat and empty for deletedCat
        Optional<Category> activeFound = categoryRepository.findByCategoryIdAndDeletedFalse(activeCat.getCategoryId());
        assertTrue(activeFound.isPresent(), "Active category must be found by ID");

        Optional<Category> deletedFound = categoryRepository.findByCategoryIdAndDeletedFalse(deletedCat.getCategoryId());
        assertTrue(deletedFound.isEmpty(), "Soft-deleted category must return Optional.empty()");
    }

    @Test
    @DisplayName("Verify PostgreSQL partial unique index: duplicate slug fails for active categories")
    void testDuplicateSlugFailsForActiveCategories() {
        String sharedSlug = "shared-slug-" + UUID.randomUUID();

        Category cat1 = new Category();
        cat1.setName("Category One");
        cat1.setSlug(sharedSlug);
        cat1.setDeleted(false);

        Category cat2 = new Category();
        cat2.setName("Category Two");
        cat2.setSlug(sharedSlug); // Same slug!
        cat2.setDeleted(false);

        categoryRepository.save(cat1);
        entityManager.flush();

        assertThrows(DataIntegrityViolationException.class, () -> {
            categoryRepository.save(cat2);
            entityManager.flush();
        }, "Two active categories with identical slug must violate ux_categories_slug partial index");
    }

    @Test
    @DisplayName("Verify PostgreSQL partial unique index: reuse slug allowed if previous category is deleted")
    void testSlugReuseAllowedWhenPreviousCategoryIsSoftDeleted() {
        String reusedSlug = "reusable-slug-" + UUID.randomUUID();

        // 1. Insert a soft-deleted category with this slug
        Category oldDeletedCat = new Category();
        oldDeletedCat.setName("Old Deleted Category");
        oldDeletedCat.setSlug(reusedSlug);
        oldDeletedCat.setDeleted(true); // is_deleted = true

        categoryRepository.save(oldDeletedCat);
        entityManager.flush();

        // 2. Insert a new ACTIVE category with the EXACT same slug
        Category newActiveCat = new Category();
        newActiveCat.setName("New Active Category");
        newActiveCat.setSlug(reusedSlug);
        newActiveCat.setDeleted(false); // is_deleted = false

        // In PostgreSQL with partial index "WHERE is_deleted = false", this MUST succeed!
        assertDoesNotThrow(() -> {
            categoryRepository.save(newActiveCat);
            entityManager.flush();
        }, "PostgreSQL partial unique index must permit slug reuse when existing record is soft-deleted");

        entityManager.clear();
        Optional<Category> activeFound = categoryRepository.findByCategoryIdAndDeletedFalse(newActiveCat.getCategoryId());
        assertTrue(activeFound.isPresent());
        assertEquals(reusedSlug, activeFound.get().getSlug());
    }
}
