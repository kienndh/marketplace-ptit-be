package com.ptiters.marketplace.category;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CategoryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
            assertEquals("marketplace_java_test", dbName,
                    "SAFETY VIOLATION: Category integration tests are strictly forbidden outside marketplace_java_test!");
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
    @DisplayName("T15: When no active categories exist, GET returns HTTP 200 with []")
    void testListCategoriesEmptyReturns200WithEmptyArray() throws Exception {
        // marketplace_java_test starts clean with 0 records
        mockMvc.perform(get("/api/v1/categories")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", is(empty())));
    }

    @Test
    @DisplayName("T01-T07, T12, T16, T17: GET /api/v1/categories returns direct array, ordered by category_id ASC, excluding soft-deleted, matching API contract")
    void testListCategoriesSuccessAndContractParity() throws Exception {
        // Prepare synthetic categories: two active, one soft-deleted
        Category cat1 = createSyntheticCategory("Alpha Books", false);
        Category cat2 = createSyntheticCategory("Beta Electronics", false);
        Category catDeleted = createSyntheticCategory("Old Archival", true);

        categoryRepository.save(cat1);
        categoryRepository.save(cat2);
        categoryRepository.save(catDeleted);
        entityManager.flush();
        entityManager.clear();

        Integer id1 = cat1.getCategoryId();
        Integer id2 = cat2.getCategoryId();

        // Anonymous access without Authorization header
        mockMvc.perform(get("/api/v1/categories")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // T02: Direct JSON array, not an object wrapper
                .andExpect(jsonPath("$", is(iterableWithSize(2))))
                // T06: Ordered by category_id ascending
                .andExpect(jsonPath("$[0].category_id", is(id1)))
                .andExpect(jsonPath("$[0].name", is(cat1.getName())))
                .andExpect(jsonPath("$[0].slug", is(cat1.getSlug())))
                .andExpect(jsonPath("$[1].category_id", is(id2)))
                .andExpect(jsonPath("$[1].name", is(cat2.getName())))
                .andExpect(jsonPath("$[1].slug", is(cat2.getSlug())))
                // T04: Must NOT contain camelCase categoryId
                .andExpect(jsonPath("$[0].categoryId").doesNotExist())
                .andExpect(jsonPath("$[1].categoryId").doesNotExist())
                // T05: Must NOT expose internal or audit fields
                .andExpect(jsonPath("$[0].is_deleted").doesNotExist())
                .andExpect(jsonPath("$[0].deleted").doesNotExist())
                .andExpect(jsonPath("$[0].created_at").doesNotExist())
                .andExpect(jsonPath("$[0].createdAt").doesNotExist())
                // T07: Soft-deleted category is excluded
                .andExpect(jsonPath("$[*].slug", not(hasItem(catDeleted.getSlug()))));
    }

    @Test
    @DisplayName("T08, T12, T16, T17: GET /api/v1/categories/{category_id} with valid active ID returns 200 with correct fields")
    void testGetActiveCategoryByIdSuccess() throws Exception {
        Category category = createSyntheticCategory("Stationery", false);
        categoryRepository.save(category);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/categories/{category_id}", category.getCategoryId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.category_id", is(category.getCategoryId())))
                .andExpect(jsonPath("$.name", is(category.getName())))
                .andExpect(jsonPath("$.slug", is(category.getSlug())))
                .andExpect(jsonPath("$.categoryId").doesNotExist())
                .andExpect(jsonPath("$.is_deleted").doesNotExist())
                .andExpect(jsonPath("$.created_at").doesNotExist());
    }

    @Test
    @DisplayName("T09, T11: GET /api/v1/categories/{category_id} with nonexistent ID returns 404 with exact detail message")
    void testGetCategoryByIdNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/categories/{category_id}", 999999)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.detail", is("Category not found.")))
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("T10, T11: GET /api/v1/categories/{category_id} with soft-deleted ID returns 404 with exact detail message")
    void testGetSoftDeletedCategoryByIdReturns404() throws Exception {
        Category deletedCategory = createSyntheticCategory("Decommissioned", true);
        categoryRepository.save(deletedCategory);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/categories/{category_id}", deletedCategory.getCategoryId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.detail", is("Category not found.")))
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("T13: Non-integer path parameter is handled safely with 422 Unprocessable Entity, not 500")
    void testNonIntegerPathParameterReturns422() throws Exception {
        mockMvc.perform(get("/api/v1/categories/{category_id}", "invalid_slug_string")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.detail", is("Invalid value for request parameter.")));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -99})
    @DisplayName("T14: Non-positive Category IDs return 404 Not Found")
    void testNonPositiveIdsReturn404(int nonPositiveId) throws Exception {
        mockMvc.perform(get("/api/v1/categories/{category_id}", nonPositiveId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", is("Category not found.")));
    }
}
