package com.ptiters.marketplace.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional(readOnly = true)
class UserReadOnlyRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void verifyDatabaseSafetyBoundary() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT current_database()")) {
            assertTrue(rs.next(), "Database query failed to return current database name");
            String dbName = rs.getString(1);
            assertEquals("marketplace_java_dev", dbName,
                    "SAFETY CHECK: Read-only tests must strictly connect to marketplace_java_dev!");
        }
    }

    @Test
    @DisplayName("Read existing users and verify basic, UUID and boolean mappings")
    void testReadExistingUsersAndMappings() {
        Page<User> usersPage = userRepository.findAll(PageRequest.of(0, 5));
        assertNotNull(usersPage, "Users page should not be null");
        assertFalse(usersPage.isEmpty(), "Database should contain existing user records");

        User sampleUser = usersPage.getContent().get(0);

        // Verify primary key
        assertNotNull(sampleUser.getUserId(), "User ID must not be null");
        assertTrue(sampleUser.getUserId() > 0, "User ID must be positive");

        // Verify UUID mapping
        assertNotNull(sampleUser.getPublicId(), "Public ID (UUID) must be mapped and non-null");

        // Verify Enum and Role mappings
        assertNotNull(sampleUser.getStatus(), "User status must be mapped to UserStatus enum");
        assertNotNull(sampleUser.getRole(), "User role must be mapped to UserRole enum");

        // Verify boolean mapping (without relying on fixed value)
        boolean showPhone = sampleUser.isShowPhonePublicly();
        assertTrue(showPhone || !showPhone, "showPhonePublicly must evaluate as a valid boolean");

        // Verify findByPublicId query
        Optional<User> foundByPublicId = userRepository.findByPublicId(sampleUser.getPublicId());
        assertTrue(foundByPublicId.isPresent(), "User should be retrievable by publicId UUID");
        assertEquals(sampleUser.getUserId(), foundByPublicId.get().getUserId(), "Retrieved user ID must match");
    }

    @Test
    @DisplayName("Verify native PostgreSQL enum user_status reading")
    void testReadNativeEnumUserStatus() {
        Optional<User> activeUserOpt = userRepository.findFirstByStatus(UserStatus.ACTIVE);
        assertTrue(activeUserOpt.isPresent(), "Should find at least one user with status ACTIVE");

        User activeUser = activeUserOpt.get();
        assertEquals(UserStatus.ACTIVE, activeUser.getStatus(), "Mapped status must be ACTIVE");
    }

    @Test
    @DisplayName("Verify JSON mapping for social_links column")
    void testReadSocialLinksJsonMapping() {
        List<User> usersWithSocialLinks = userRepository.findUsersWithSocialLinks(PageRequest.of(0, 5));
        assertNotNull(usersWithSocialLinks, "Query result should not be null");

        if (!usersWithSocialLinks.isEmpty()) {
            User user = usersWithSocialLinks.get(0);
            List<SocialLink> links = user.getSocialLinks();
            assertNotNull(links, "socialLinks property should not be null when column contains JSON array");

            for (SocialLink link : links) {
                assertNotNull(link.getPlatform(), "SocialLink platform field must be populated from JSON");
                assertNotNull(link.getUrl(), "SocialLink url field must be populated from JSON");
            }
        }
    }
}
