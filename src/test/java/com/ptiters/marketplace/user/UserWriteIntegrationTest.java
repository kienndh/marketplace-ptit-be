package com.ptiters.marketplace.user;

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
class UserWriteIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @BeforeEach
    void verifyDatabaseSafetyBoundary() throws SQLException {
        // Assert the connected database is exactly marketplace_java_test
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT current_database()")) {
            assertTrue(rs.next(), "Database query failed to return current database name");
            String dbName = rs.getString(1);
            assertEquals("marketplace_java_test", dbName,
                    "SAFETY VIOLATION: Write tests are strictly forbidden outside marketplace_java_test!");
        }

        // Verify Flyway is disabled and Hibernate ddl-auto is validate
        assertEquals("false", environment.getProperty("spring.flyway.enabled"),
                "Flyway must be disabled during write tests");
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"),
                "Hibernate ddl-auto must remain validate");
    }

    private User createSyntheticUser(String emailPrefix) {
        User user = new User();
        user.setEmail(emailPrefix + "_" + UUID.randomUUID() + "@synthetic.local");
        user.setFullName("Synthetic Test User");
        user.setPhone("0987654321");
        user.setShowPhonePublicly(true);
        user.setRole(UserRole.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
        user.setBio("Synthetic test bio");
        user.setDeliveryArea("Ha Dong, Ha Noi");
        user.setSocialLinks(List.of(
                new SocialLink("GITHUB", "https://github.com/synthetic"),
                new SocialLink("LINKEDIN", "https://linkedin.com/in/synthetic")
        ));
        return user;
    }

    @Test
    @DisplayName("Verify synthetic user INSERT generates and preserves UUID and returns PK")
    void testInsertUserGeneratesAndPreservesUuid() {
        User user = createSyntheticUser("user_uuid");
        assertNull(user.getPublicId(), "UUID should be null before persist");

        User savedUser = userRepository.save(user);
        entityManager.flush();

        assertNotNull(savedUser.getUserId(), "Primary key user_id must be generated and populated");
        assertTrue(savedUser.getUserId() > 0, "Primary key must be a positive Long");
        assertNotNull(savedUser.getPublicId(), "publicId must be generated via @PrePersist on persist");

        UUID generatedUuid = savedUser.getPublicId();

        // Clear persistence context and reload from PostgreSQL
        entityManager.clear();
        Optional<User> reloadedOpt = userRepository.findById(savedUser.getUserId());
        assertTrue(reloadedOpt.isPresent(), "Persisted user must be reloadable from DB");

        User reloadedUser = reloadedOpt.get();
        assertEquals(generatedUuid, reloadedUser.getPublicId(),
                "Reloaded UUID must exactly match the generated UUID");
    }

    @Test
    @DisplayName("Verify native PostgreSQL enum user_status can be inserted and updated")
    void testInsertAndUpdateNativeEnumStatus() {
        User user = createSyntheticUser("user_status");
        user.setStatus(UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);
        entityManager.flush();

        // Update status to BANNED
        savedUser.setStatus(UserStatus.BANNED);
        userRepository.save(savedUser);
        entityManager.flush();

        // Clear persistence context and reload
        entityManager.clear();
        User reloadedUser = userRepository.findById(savedUser.getUserId()).orElseThrow();
        assertEquals(UserStatus.BANNED, reloadedUser.getStatus(),
                "Status must be updated to BANNED in native PostgreSQL enum");

        // Update status to INACTIVE
        reloadedUser.setStatus(UserStatus.INACTIVE);
        userRepository.save(reloadedUser);
        entityManager.flush();

        entityManager.clear();
        User reloadedAgain = userRepository.findById(savedUser.getUserId()).orElseThrow();
        assertEquals(UserStatus.INACTIVE, reloadedAgain.getStatus(),
                "Status must be updated to INACTIVE in native PostgreSQL enum");
    }

    @Test
    @DisplayName("Verify socialLinks JSON and showPhonePublicly boolean are written and read back correctly")
    void testWriteAndReadJsonAndBooleanFields() {
        User user = createSyntheticUser("user_json");
        user.setShowPhonePublicly(true);
        user.setSocialLinks(List.of(
                new SocialLink("FACEBOOK", "https://facebook.com/synthetic_user"),
                new SocialLink("GITHUB", "https://github.com/synthetic_dev")
        ));

        User savedUser = userRepository.save(user);
        entityManager.flush();

        entityManager.clear();
        User reloadedUser = userRepository.findById(savedUser.getUserId()).orElseThrow();

        // Verify boolean
        assertTrue(reloadedUser.isShowPhonePublicly(),
                "showPhonePublicly must persist true");

        // Verify JSON list
        List<SocialLink> links = reloadedUser.getSocialLinks();
        assertNotNull(links, "socialLinks must not be null after reload");
        assertEquals(2, links.size(), "socialLinks must retain 2 items");
        assertEquals("FACEBOOK", links.get(0).getPlatform());
        assertEquals("https://facebook.com/synthetic_user", links.get(0).getUrl());
        assertEquals("GITHUB", links.get(1).getPlatform());
        assertEquals("https://github.com/synthetic_dev", links.get(1).getUrl());
    }

    @Test
    @DisplayName("Verify unique email constraint is enforced by PostgreSQL")
    void testUniqueEmailConstraintViolation() {
        User user1 = createSyntheticUser("user_unique");
        userRepository.save(user1);
        entityManager.flush();

        // Create second user with identical email
        User user2 = createSyntheticUser("user_unique_duplicate");
        user2.setEmail(user1.getEmail()); // duplicate email!

        assertThrows(DataIntegrityViolationException.class, () -> {
            userRepository.save(user2);
            entityManager.flush();
        }, "PostgreSQL ux_users_email unique constraint must throw DataIntegrityViolationException");
    }
}
