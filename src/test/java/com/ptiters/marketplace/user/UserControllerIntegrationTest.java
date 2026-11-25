package com.ptiters.marketplace.user;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.ptiters.marketplace.configuration.JwtConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerIntegrationTest {

    private static final String TEST_PROJECT_ID = "test-marketplace-ptit";
    private static final String TEST_ISSUER = "https://securetoken.google.com/" + TEST_PROJECT_ID;
    private static final String TEST_KID = "test-user-controller-key-001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuthIdentityRepository userAuthIdentityRepository;

    @Autowired
    private UserCapabilityRestrictionRepository restrictionRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @TestConfiguration
    static class TestControllerSecurityConfig {
        static final KeyPair KEY_PAIR;
        static {
            try {
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
                kpg.initialize(2048);
                KEY_PAIR = kpg.generateKeyPair();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Bean
        @Primary
        public JwtDecoder testJwtDecoder() {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY_PAIR.getPublic()).build();
            decoder.setJwtValidator(JwtConfig.createFirebaseJwtValidator(TEST_PROJECT_ID));
            return decoder;
        }
    }

    @BeforeEach
    void verifyDatabaseSafetyBoundaryAndReset() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT current_database()")) {
            assertTrue(rs.next(), "Database query failed to return current database name");
            String dbName = rs.getString(1);
            org.junit.jupiter.api.Assertions.assertTrue(dbName.matches("marketplace_java_test(_[a-z0-9]+)?"),
                    "SAFETY VIOLATION: Tests are strictly forbidden outside marketplace_java_test!");
        }

        assertEquals("false", environment.getProperty("spring.flyway.enabled"),
                "Flyway must be disabled during write tests");
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"),
                "Hibernate ddl-auto must remain validate");

        cleanDatabase();
    }

    @AfterEach
    void cleanUpAfterTest() throws SQLException {
        cleanDatabase();
    }

    private void cleanDatabase() throws SQLException {
        restrictionRepository.deleteAllInBatch();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM reports");
        }
        userAuthIdentityRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private String createSignedToken(String sub, String email, Boolean emailVerified, String name, String picture) throws Exception {
        Instant now = Instant.now();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(TEST_KID)
                .type(JOSEObjectType.JWT)
                .build();

        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(TEST_ISSUER)
                .subject(sub)
                .audience(TEST_PROJECT_ID)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .claim("auth_time", now.getEpochSecond());

        if (email != null) {
            builder.claim("email", email);
        }
        if (emailVerified != null) {
            builder.claim("email_verified", emailVerified);
        }
        if (name != null) {
            builder.claim("name", name);
        }
        if (picture != null) {
            builder.claim("picture", picture);
        }

        SignedJWT signedJWT = new SignedJWT(header, builder.build());
        signedJWT.sign(new RSASSASigner(TestControllerSecurityConfig.KEY_PAIR.getPrivate()));
        return signedJWT.serialize();
    }

    private User createSyntheticUser(String email, UserRole role, UserStatus status) {
        User user = new User();
        user.setEmail(email);
        user.setFullName("Synthetic " + email);
        user.setRole(role);
        user.setStatus(status);
        user.setPublicId(UUID.randomUUID());
        user.setBio("Sinh vien PTIT");
        user.setDeliveryArea("Ha Dong, Ha Noi");
        user.setShowPhonePublicly(false);
        user.setLastActiveAt(LocalDateTime.now(ZoneOffset.UTC));
        return userRepository.saveAndFlush(user);
    }

    private UserAuthIdentity createSyntheticIdentity(User user, String providerUid) {
        UserAuthIdentity identity = new UserAuthIdentity();
        identity.setUser(user);
        identity.setProvider(UserAuthIdentity.PROVIDER_FIREBASE);
        identity.setProviderUid(providerUid);
        return userAuthIdentityRepository.saveAndFlush(identity);
    }

    private UserCapabilityRestriction createSyntheticRestriction(User user, User admin, String capability, LocalDateTime expiresAt) throws SQLException {
        long reportId;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(
                    "INSERT INTO reports (reporter_id, target_type, target_id, reason, status) " +
                    "VALUES (" + admin.getUserId() + ", 'USER', " + user.getUserId() + ", 'SCAM', 'RESOLVED') RETURNING report_id")) {
                rs.next();
                reportId = rs.getLong(1);
            }
        }

        UserCapabilityRestriction restriction = new UserCapabilityRestriction();
        restriction.setUserId(user.getUserId());
        restriction.setSourceReportId(reportId);
        restriction.setImposedBy(admin.getUserId());
        restriction.setCapability(capability);
        restriction.setStartsAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
        restriction.setExpiresAt(expiresAt);
        return restrictionRepository.saveAndFlush(restriction);
    }

    @Test
    @DisplayName("USER01: GET /api/v1/users/me without Authorization header returns 401 Unauthorized")
    void testUser01_getMe_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.detail", is("Missing or invalid Authorization header.")));
    }

    @Test
    @DisplayName("USER02: GET /api/v1/users/me with malformed / invalid token returns 401 Unauthorized")
    void testUser02_getMe_withMalformedToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.signature"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.detail", is("Missing or invalid Authorization header.")));
    }

    @Test
    @DisplayName("USER03: GET /api/v1/users/me with valid token and ACTIVE user returns 200 OK with correct JSON")
    void testUser03_getMe_activeUser_returns200WithCorrectJson() throws Exception {
        String uid = "firebase-user03-uid";
        String email = "student03@ptit.edu.vn";
        User user = createSyntheticUser(email, UserRole.STUDENT, UserStatus.ACTIVE);
        createSyntheticIdentity(user, uid);

        String token = createSignedToken(uid, email, true, "Nguyen Van C", "https://avatar.url/c.png");

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id", is(user.getUserId().intValue())))
                .andExpect(jsonPath("$.public_id", is(user.getPublicId().toString())))
                .andExpect(jsonPath("$.email", is(email)))
                .andExpect(jsonPath("$.full_name", is(user.getFullName())))
                .andExpect(jsonPath("$.role", is("STUDENT")))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.show_phone_publicly", is(false)))
                .andExpect(jsonPath("$.bio", is("Sinh vien PTIT")))
                .andExpect(jsonPath("$.delivery_area", is("Ha Dong, Ha Noi")))
                .andExpect(jsonPath("$.last_active_at", notNullValue()))
                .andExpect(jsonPath("$.created_at", notNullValue()))
                .andExpect(jsonPath("$.updated_at", notNullValue()));
    }

    @Test
    @DisplayName("USER04: JWT Firebase UID maps to the exact internal User record")
    void testUser04_getMe_uidMapsToCorrectInternalUser() throws Exception {
        User userA = createSyntheticUser("userA@ptit.edu.vn", UserRole.STUDENT, UserStatus.ACTIVE);
        createSyntheticIdentity(userA, "firebase-uid-userA");

        User userB = createSyntheticUser("userB@ptit.edu.vn", UserRole.STUDENT, UserStatus.ACTIVE);
        createSyntheticIdentity(userB, "firebase-uid-userB");

        String tokenB = createSignedToken("firebase-uid-userB", "userB@ptit.edu.vn", true, "User B", null);

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id", is(userB.getUserId().intValue())))
                .andExpect(jsonPath("$.email", is("userB@ptit.edu.vn")));
    }

    @Test
    @DisplayName("USER05: Existing identity resolution is idempotent and does not create duplicate User records")
    void testUser05_getMe_existingIdentity_doesNotDuplicateUser() throws Exception {
        User user = createSyntheticUser("idempotent@ptit.edu.vn", UserRole.STUDENT, UserStatus.ACTIVE);
        createSyntheticIdentity(user, "firebase-idempotent-user-uid");

        String token = createSignedToken("firebase-idempotent-user-uid", "idempotent@ptit.edu.vn", true, "Idempotent", null);

        // First call
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // Second call
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        assertEquals(1, userRepository.count());
        assertEquals(1, userAuthIdentityRepository.count());
    }

    @Test
    @DisplayName("USER06: First login triggers JIT provisioning and returns 200 OK with newly created User")
    void testUser06_getMe_firstLogin_provisionsUserAndIdentityAtomically() throws Exception {
        String uid = "firebase-brand-new-firstlogin-uid";
        String email = "firstlogin@ptit.edu.vn";
        String token = createSignedToken(uid, email, true, "First Login Student", "https://img.local/avatar.jpg");

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(email)))
                .andExpect(jsonPath("$.full_name", is("First Login Student")))
                .andExpect(jsonPath("$.role", is("STUDENT")))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.user_id", notNullValue()));

        assertEquals(1, userRepository.count());
        assertEquals(1, userAuthIdentityRepository.count());
    }

    @Test
    @DisplayName("USER07: Untrusted client claims cannot manipulate role or status (privilege escalation prevented)")
    void testUser07_getMe_privilegeEscalationPrevented_clientClaimsIgnored() throws Exception {
        String uid = "firebase-spoof-attempt-uid";
        Instant now = Instant.now();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(TEST_KID)
                .type(JOSEObjectType.JWT)
                .build();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(TEST_ISSUER)
                .subject(uid)
                .audience(TEST_PROJECT_ID)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .claim("auth_time", now.getEpochSecond())
                .claim("email", "hacker@ptit.edu.vn")
                .claim("email_verified", true)
                .claim("name", "Hacker")
                .claim("role", "ADMIN")
                .claim("status", "BANNED")
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(new RSASSASigner(TestControllerSecurityConfig.KEY_PAIR.getPrivate()));
        String token = signedJWT.serialize();

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("STUDENT")))
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    @DisplayName("USER08: BANNED user calling /api/v1/users/me returns 403 Forbidden with exact detail")
    void testUser08_getMe_bannedUser_returns403Forbidden() throws Exception {
        String uid = "firebase-banned-user-uid";
        String email = "banned.student@ptit.edu.vn";
        User user = createSyntheticUser(email, UserRole.STUDENT, UserStatus.BANNED);
        createSyntheticIdentity(user, uid);

        String token = createSignedToken(uid, email, true, "Banned Student", null);

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", is("User account is banned.")));
    }

    @Test
    @DisplayName("USER09: INACTIVE user calling /api/v1/users/me returns 403 Forbidden with exact detail")
    void testUser09_getMe_inactiveUser_returns403Forbidden() throws Exception {
        String uid = "firebase-inactive-user-uid";
        String email = "inactive.student@ptit.edu.vn";
        User user = createSyntheticUser(email, UserRole.STUDENT, UserStatus.INACTIVE);
        createSyntheticIdentity(user, uid);

        String token = createSignedToken(uid, email, true, "Inactive Student", null);

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", is("User account is inactive.")));
    }

    @Test
    @DisplayName("USER10: BANNED user calling /api/v1/users/me/access-status returns 200 OK with status BANNED")
    void testUser10_getAccessStatus_bannedUser_returns200WithBannedStatus() throws Exception {
        String uid = "firebase-banned-access-uid";
        String email = "banned.access@ptit.edu.vn";
        User user = createSyntheticUser(email, UserRole.STUDENT, UserStatus.BANNED);
        createSyntheticIdentity(user, uid);

        String token = createSignedToken(uid, email, true, "Banned Access User", null);

        mockMvc.perform(get("/api/v1/users/me/access-status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id", is(user.getUserId().intValue())))
                .andExpect(jsonPath("$.status", is("BANNED")))
                .andExpect(jsonPath("$.role", is("STUDENT")))
                .andExpect(jsonPath("$.can_buy", is(false)))
                .andExpect(jsonPath("$.can_sell", is(false)))
                .andExpect(jsonPath("$.active_restrictions", empty()));
    }

    @Test
    @DisplayName("USER11: INACTIVE user calling /api/v1/users/me/access-status returns 200 OK with status INACTIVE")
    void testUser11_getAccessStatus_inactiveUser_returns200WithInactiveStatus() throws Exception {
        String uid = "firebase-inactive-access-uid";
        String email = "inactive.access@ptit.edu.vn";
        User user = createSyntheticUser(email, UserRole.STUDENT, UserStatus.INACTIVE);
        createSyntheticIdentity(user, uid);

        String token = createSignedToken(uid, email, true, "Inactive Access User", null);

        mockMvc.perform(get("/api/v1/users/me/access-status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id", is(user.getUserId().intValue())))
                .andExpect(jsonPath("$.status", is("INACTIVE")))
                .andExpect(jsonPath("$.role", is("STUDENT")))
                .andExpect(jsonPath("$.can_buy", is(false)))
                .andExpect(jsonPath("$.can_sell", is(false)))
                .andExpect(jsonPath("$.active_restrictions", empty()));
    }

    @Test
    @DisplayName("USER12: ACTIVE unrestricted user calling /api/v1/users/me/access-status returns 200 OK with can_buy/sell true")
    void testUser12_getAccessStatus_activeUser_returns200WithActiveStatus() throws Exception {
        String uid = "firebase-active-access-uid";
        String email = "active.access@ptit.edu.vn";
        User user = createSyntheticUser(email, UserRole.STUDENT, UserStatus.ACTIVE);
        createSyntheticIdentity(user, uid);

        String token = createSignedToken(uid, email, true, "Active Access User", null);

        mockMvc.perform(get("/api/v1/users/me/access-status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id", is(user.getUserId().intValue())))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.role", is("STUDENT")))
                .andExpect(jsonPath("$.can_buy", is(true)))
                .andExpect(jsonPath("$.can_sell", is(true)))
                .andExpect(jsonPath("$.active_restrictions", empty()));
    }

    @Test
    @DisplayName("USER13: Active user with effective SELL restriction returns can_sell false and restriction metadata")
    void testUser13_getAccessStatus_withSellRestriction_returnsCanSellFalseAndMetadata() throws Exception {
        User admin = createSyntheticUser("admin@ptit.edu.vn", UserRole.ADMIN, UserStatus.ACTIVE);
        User student = createSyntheticUser("restricted.student@ptit.edu.vn", UserRole.STUDENT, UserStatus.ACTIVE);
        createSyntheticIdentity(student, "firebase-restricted-student-uid");

        LocalDateTime tomorrow = LocalDateTime.now(ZoneOffset.UTC).plusDays(1);
        createSyntheticRestriction(student, admin, "SELL", tomorrow);

        String token = createSignedToken("firebase-restricted-student-uid", "restricted.student@ptit.edu.vn", true, "Restricted", null);

        mockMvc.perform(get("/api/v1/users/me/access-status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.can_buy", is(true)))
                .andExpect(jsonPath("$.can_sell", is(false)))
                .andExpect(jsonPath("$.active_restrictions", hasSize(1)))
                .andExpect(jsonPath("$.active_restrictions[0].capability", is("SELL")))
                .andExpect(jsonPath("$.active_restrictions[0].expires_at", notNullValue()));
    }

    @Test
    @DisplayName("USER14: Invalid Authorization header cannot trigger new-user provisioning")
    void testUser14_getMe_invalidAuth_cannotTriggerJitProvisioning() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());

        assertEquals(0, userRepository.count());
        assertEquals(0, userAuthIdentityRepository.count());
    }

    @Test
    @DisplayName("USER15: Category list endpoint remains public and accessible without authentication")
    void testUser15_categoryList_remainsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("USER16: Category detail endpoint remains public and not challenged with 401")
    void testUser16_categoryDetail_remainsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/categories/999999"))
                .andExpect(status().isNotFound()); // 404 Not Found, NOT 401 Unauthorized
    }

    @Test
    @DisplayName("USER17: Public API requests do not invoke JIT User creation or identity writes")
    void testUser17_publicApiRequests_doNotInvokeJitUserCreation() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk());

        assertEquals(0, userRepository.count(), "Public GET must not trigger User writes");
        assertEquals(0, userAuthIdentityRepository.count(), "Public GET must not trigger Identity writes");
    }

    @Test
    @DisplayName("USER18: JSON serialization strictly uses snake_case matching Python Pydantic contract")
    void testUser18_jsonSnakeCase_matchesPythonSchema() throws Exception {
        String uid = "firebase-snake-case-uid";
        String email = "snakecase@ptit.edu.vn";
        User user = createSyntheticUser(email, UserRole.STUDENT, UserStatus.ACTIVE);
        createSyntheticIdentity(user, uid);

        String token = createSignedToken(uid, email, true, "Snake Case Student", null);

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").exists())
                .andExpect(jsonPath("$.public_id").exists())
                .andExpect(jsonPath("$.full_name").exists())
                .andExpect(jsonPath("$.show_phone_publicly").exists())
                .andExpect(jsonPath("$.avatar_url").hasJsonPath())
                .andExpect(jsonPath("$.delivery_area").exists())
                .andExpect(jsonPath("$.default_meetup_area").hasJsonPath())
                .andExpect(jsonPath("$.last_active_at").exists())
                .andExpect(jsonPath("$.created_at").exists())
                .andExpect(jsonPath("$.updated_at").exists());
    }

    @Test
    @DisplayName("USER19: Private or internal fields (provider_uid, password, JPA internals) do not leak")
    void testUser19_noPrivateOrInternalFieldsLeak() throws Exception {
        String uid = "firebase-privacy-uid";
        String email = "privacy@ptit.edu.vn";
        User user = createSyntheticUser(email, UserRole.STUDENT, UserStatus.ACTIVE);
        createSyntheticIdentity(user, uid);

        String token = createSignedToken(uid, email, true, "Privacy Student", null);

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider_uid").doesNotExist())
                .andExpect(jsonPath("$.provider").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.hibernateLazyInitializer").doesNotExist());
    }

    @Test
    @DisplayName("USER20: Account linking conflict returns HTTP 409 Conflict with standard ErrorResponse JSON")
    void testUser20_accountLinkingConflict_returns409ConflictNot500() throws Exception {
        User victim = createSyntheticUser("conflict.victim@ptit.edu.vn", UserRole.STUDENT, UserStatus.ACTIVE);
        createSyntheticIdentity(victim, "legit-owner-uid");

        // Attacker attempts to login with victim's email under different UID
        String attackerUid = "attacker-uid-666";
        String token = createSignedToken(attackerUid, "conflict.victim@ptit.edu.vn", true, "Attacker", null);

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", is("Account already linked to a different provider identity.")));
    }

    @Test
    @DisplayName("USER21: PATCH /api/v1/users/me successfully updates profile fields (partial update)")
    void testUser21_updateMyProfile_success() throws Exception {
        String uid = "firebase-user21-uid";
        String email = "student21@ptit.edu.vn";
        User user = createSyntheticUser(email, UserRole.STUDENT, UserStatus.ACTIVE);
        createSyntheticIdentity(user, uid);

        String token = createSignedToken(uid, email, true, "Old Name", null);

        String payloadJson = """
            {
                "full_name": "Nguyen Van A Updated",
                "phone": "0987654321",
                "show_phone_publicly": true,
                "bio": "Sinh vien nam cuoi D21",
                "delivery_area": "Co Nhue, Bac Tu Liem",
                "default_meetup_area": "Cong chinh PTIT",
                "social_links": [
                    {"platform": "FACEBOOK", "url": "https://facebook.com/ptiter.d21"},
                    {"platform": "ZALO", "url": "https://zalo.me/0987654321"}
                ]
            }
            """;

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.full_name", is("Nguyen Van A Updated")))
                .andExpect(jsonPath("$.phone", is("0987654321")))
                .andExpect(jsonPath("$.show_phone_publicly", is(true)))
                .andExpect(jsonPath("$.bio", is("Sinh vien nam cuoi D21")))
                .andExpect(jsonPath("$.delivery_area", is("Co Nhue, Bac Tu Liem")))
                .andExpect(jsonPath("$.default_meetup_area", is("Cong chinh PTIT")))
                .andExpect(jsonPath("$.social_links", hasSize(2)))
                .andExpect(jsonPath("$.social_links[0].platform", is("FACEBOOK")))
                .andExpect(jsonPath("$.social_links[0].url", is("https://facebook.com/ptiter.d21")));

        User refreshed = userRepository.findById(user.getUserId()).orElseThrow();
        assertEquals("Nguyen Van A Updated", refreshed.getFullName());
        assertEquals("0987654321", refreshed.getPhone());
        assertTrue(refreshed.isShowPhonePublicly());
        assertEquals("Sinh vien nam cuoi D21", refreshed.getBio());
    }

    @Test
    @DisplayName("USER22: PATCH /api/v1/users/me normalizes Vietnam mobile phone (+84, spaces, dashes)")
    void testUser22_updateMyProfile_normalizesPhone() throws Exception {
        String uid = "firebase-user22-uid";
        String email = "student22@ptit.edu.vn";
        User user = createSyntheticUser(email, UserRole.STUDENT, UserStatus.ACTIVE);
        createSyntheticIdentity(user, uid);

        String token = createSignedToken(uid, email, true, "User 22", null);

        String payloadJson = """
            {
                "phone": "+84 987.654-321"
            }
            """;

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone", is("0987654321")));
    }

    @Test
    @DisplayName("USER23: PATCH /api/v1/users/me with invalid phone format returns 400 Bad Request")
    void testUser23_updateMyProfile_invalidPhone_returns400() throws Exception {
        String uid = "firebase-user23-uid";
        String email = "student23@ptit.edu.vn";
        User user = createSyntheticUser(email, UserRole.STUDENT, UserStatus.ACTIVE);
        createSyntheticIdentity(user, uid);

        String token = createSignedToken(uid, email, true, "User 23", null);

        String payloadJson = """
            {
                "phone": "0123456789"
            }
            """;

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("Số điện thoại không hợp lệ")));
    }

    @Test
    @DisplayName("USER24: PATCH /api/v1/users/me validate social links domain matching platform returns 400 when invalid")
    void testUser24_updateMyProfile_invalidSocialLinkDomain_returns400() throws Exception {
        String uid = "firebase-user24-uid";
        String email = "student24@ptit.edu.vn";
        User user = createSyntheticUser(email, UserRole.STUDENT, UserStatus.ACTIVE);
        createSyntheticIdentity(user, uid);

        String token = createSignedToken(uid, email, true, "User 24", null);

        String payloadJson = """
            {
                "social_links": [
                    {"platform": "FACEBOOK", "url": "https://google.com/attacker"}
                ]
            }
            """;

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("Liên kết không thuộc Facebook")));
    }

    @Test
    @DisplayName("USER25: PATCH /api/v1/users/me ignores system fields (email, role, status) to prevent escalation")
    void testUser25_updateMyProfile_systemFieldsIgnored() throws Exception {
        String uid = "firebase-user25-uid";
        String email = "student25@ptit.edu.vn";
        User user = createSyntheticUser(email, UserRole.STUDENT, UserStatus.ACTIVE);
        createSyntheticIdentity(user, uid);

        String token = createSignedToken(uid, email, true, "User 25", null);

        String payloadJson = """
            {
                "email": "hacked@ptit.edu.vn",
                "role": "ADMIN",
                "status": "BANNED",
                "full_name": "Nguyen Legit"
            }
            """;

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(email)))
                .andExpect(jsonPath("$.role", is("STUDENT")))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.full_name", is("Nguyen Legit")));

        User refreshed = userRepository.findById(user.getUserId()).orElseThrow();
        assertEquals(email, refreshed.getEmail());
        assertEquals(UserRole.STUDENT, refreshed.getRole());
        assertEquals(UserStatus.ACTIVE, refreshed.getStatus());
    }

    @Test
    @DisplayName("USER26: PATCH /api/v1/users/me requires authentication and ACTIVE status")
    void testUser26_updateMyProfile_authAndActiveBoundary() throws Exception {
        // Without auth header -> 401
        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"full_name\": \"Test\"}"))
                .andExpect(status().isUnauthorized());

        // BANNED user -> 403
        String bannedUid = "firebase-banned-patch-uid";
        String bannedEmail = "banned.patch@ptit.edu.vn";
        User banned = createSyntheticUser(bannedEmail, UserRole.STUDENT, UserStatus.BANNED);
        createSyntheticIdentity(banned, bannedUid);

        String bannedToken = createSignedToken(bannedUid, bannedEmail, true, "Banned", null);
        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bannedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"full_name\": \"Banned Name\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", is("User account is banned.")));
    }

    @Test
    @DisplayName("USER27: GET /api/v1/users/banks is public and returns 200 OK with supported banks list")
    void testUser27_getSupportedBanks_publicEndpoint_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/users/banks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(List.class)))
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(40)))
                .andExpect(jsonPath("$[?(@.bin == '970436')].code", hasItem("VCB")))
                .andExpect(jsonPath("$[?(@.bin == '970422')].code", hasItem("MB")))
                .andExpect(jsonPath("$[?(@.bin == '963388')].code", hasItem("TIMO")));
    }

    @Test
    @DisplayName("USER28: GET /api/v1/users/{public_id} is public and hides phone when show_phone_publicly is false")
    void testUser28_getPublicUserProfile_phoneVisibility() throws Exception {
        User user = createSyntheticUser("public.profile@ptit.edu.vn", UserRole.STUDENT, UserStatus.ACTIVE);
        user.setPhone("0912345678");
        user.setShowPhonePublicly(false);
        userRepository.saveAndFlush(user);

        // When showPhonePublicly == false -> phone is null in public response
        mockMvc.perform(get("/api/v1/users/" + user.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.public_id", is(user.getPublicId().toString())))
                .andExpect(jsonPath("$.full_name", is(user.getFullName())))
                .andExpect(jsonPath("$.phone").doesNotExist()); // Or null

        // When showPhonePublicly == true -> phone is visible
        user.setShowPhonePublicly(true);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/v1/users/" + user.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone", is("0912345678")));
    }

    @Test
    @DisplayName("USER29: GET /api/v1/users/{public_id} returns 404 when user not found or not ACTIVE")
    void testUser29_getPublicUserProfile_notFoundOrInactive_returns404() throws Exception {
        // Non-existent UUID
        mockMvc.perform(get("/api/v1/users/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", is("Không tìm thấy người dùng.")));

        // BANNED user
        User banned = createSyntheticUser("banned.pub@ptit.edu.vn", UserRole.STUDENT, UserStatus.BANNED);
        mockMvc.perform(get("/api/v1/users/" + banned.getPublicId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", is("Không tìm thấy người dùng.")));
    }

    @Test
    @DisplayName("USER30: GET /api/v1/users/{public_id}/sold-products is public and returns 200 with empty list when no sales")
    void testUser30_getUserSoldProducts_publicEndpoint_returnsEmptyList() throws Exception {
        User user = createSyntheticUser("seller.test@ptit.edu.vn", UserRole.STUDENT, UserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/users/" + user.getPublicId() + "/sold-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", empty()))
                .andExpect(jsonPath("$.total_count", is(0)));

        // Non-existent UUID -> 404
        mockMvc.perform(get("/api/v1/users/" + UUID.randomUUID() + "/sold-products"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("USER31: GET /api/v1/users/me/bank-account returns 200 with null body when no bank account is set")
    void testUser31_getMyBankAccount_notSet_returns200Null() throws Exception {
        String uid = "firebase-user31-uid";
        String email = "student31@ptit.edu.vn";
        User user = createSyntheticUser(email, UserRole.STUDENT, UserStatus.ACTIVE);
        createSyntheticIdentity(user, uid);

        String token = createSignedToken(uid, email, true, "User 31", null);

        mockMvc.perform(get("/api/v1/users/me/bank-account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("USER32: PUT /api/v1/users/me/bank-account sets bank account and returns BankAccountResponse")
    void testUser32_updateMyBankAccount_success() throws Exception {
        String uid = "firebase-user32-uid";
        String email = "student32@ptit.edu.vn";
        User user = createSyntheticUser(email, UserRole.STUDENT, UserStatus.ACTIVE);
        createSyntheticIdentity(user, uid);

        String token = createSignedToken(uid, email, true, "User 32", null);

        String payloadJson = """
            {
                "bank_bin": "970436",
                "account_number": "1012345678",
                "account_name": "NGUYEN VAN A"
            }
            """;

        mockMvc.perform(put("/api/v1/users/me/bank-account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bank_bin", is("970436")))
                .andExpect(jsonPath("$.bank_code", is("VCB")))
                .andExpect(jsonPath("$.bank_short_name", is("Vietcombank")))
                .andExpect(jsonPath("$.bank_name", containsString("Ngoại Thương")))
                .andExpect(jsonPath("$.account_number", is("1012345678")))
                .andExpect(jsonPath("$.account_name", is("NGUYEN VAN A")));

        // Verify GET /me/bank-account now returns this account
        mockMvc.perform(get("/api/v1/users/me/bank-account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bank_bin", is("970436")))
                .andExpect(jsonPath("$.bank_code", is("VCB")));
    }

    @Test
    @DisplayName("USER33: PUT /api/v1/users/me/bank-account accepts bank code alias (e.g., 'MB')")
    void testUser33_updateMyBankAccount_resolvesCodeAlias() throws Exception {
        String uid = "firebase-user33-uid";
        String email = "student33@ptit.edu.vn";
        User user = createSyntheticUser(email, UserRole.STUDENT, UserStatus.ACTIVE);
        createSyntheticIdentity(user, uid);

        String token = createSignedToken(uid, email, true, "User 33", null);

        String payloadJson = """
            {
                "bank_bin": "MB",
                "account_number": "0987654321",
                "account_name": "TRAN VAN B"
            }
            """;

        mockMvc.perform(put("/api/v1/users/me/bank-account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bank_bin", is("970422")))
                .andExpect(jsonPath("$.bank_code", is("MB")))
                .andExpect(jsonPath("$.bank_short_name", is("MBBank")));
    }

    @Test
    @DisplayName("USER34: PUT /api/v1/users/me/bank-account with unsupported bank returns 400 Bad Request")
    void testUser34_updateMyBankAccount_unsupportedBank_returns400() throws Exception {
        String uid = "firebase-user34-uid";
        String email = "student34@ptit.edu.vn";
        User user = createSyntheticUser(email, UserRole.STUDENT, UserStatus.ACTIVE);
        createSyntheticIdentity(user, uid);

        String token = createSignedToken(uid, email, true, "User 34", null);

        String payloadJson = """
            {
                "bank_bin": "999999",
                "account_number": "1012345678",
                "account_name": "NGUYEN VAN A"
            }
            """;

        mockMvc.perform(put("/api/v1/users/me/bank-account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("không hợp lệ hoặc không được hỗ trợ")));
    }

    @Test
    @DisplayName("USER35: PUT /api/v1/users/me/bank-account with non-digit account number returns 400 Bad Request")
    void testUser35_updateMyBankAccount_invalidAccountNumber_returns400() throws Exception {
        String uid = "firebase-user35-uid";
        String email = "student35@ptit.edu.vn";
        User user = createSyntheticUser(email, UserRole.STUDENT, UserStatus.ACTIVE);
        createSyntheticIdentity(user, uid);

        String token = createSignedToken(uid, email, true, "User 35", null);

        String payloadJson = """
            {
                "bank_bin": "970436",
                "account_number": "101234ABCD",
                "account_name": "NGUYEN VAN A"
            }
            """;

        mockMvc.perform(put("/api/v1/users/me/bank-account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("chỉ được chứa chữ số")));
    }

    @Test
    @DisplayName("USER36: PUT /api/v1/users/me/bank-account with digits in account name returns 400 Bad Request")
    void testUser36_updateMyBankAccount_invalidAccountName_returns400() throws Exception {
        String uid = "firebase-user36-uid";
        String email = "student36@ptit.edu.vn";
        User user = createSyntheticUser(email, UserRole.STUDENT, UserStatus.ACTIVE);
        createSyntheticIdentity(user, uid);

        String token = createSignedToken(uid, email, true, "User 36", null);

        String payloadJson = """
            {
                "bank_bin": "970436",
                "account_number": "1012345678",
                "account_name": "NGUYEN 123"
            }
            """;

        mockMvc.perform(put("/api/v1/users/me/bank-account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("chỉ được chứa chữ cái")));
    }

    @Test
    @DisplayName("USER37: DELETE /api/v1/users/me/bank-account clears bank info and returns 204 No Content")
    void testUser37_deleteMyBankAccount_success() throws Exception {
        String uid = "firebase-user37-uid";
        String email = "student37@ptit.edu.vn";
        User user = createSyntheticUser(email, UserRole.STUDENT, UserStatus.ACTIVE);
        user.setBankBin("970436");
        user.setBankAccountNumber("1012345678");
        user.setBankAccountName("NGUYEN VAN A");
        userRepository.saveAndFlush(user);
        createSyntheticIdentity(user, uid);

        String token = createSignedToken(uid, email, true, "User 37", null);

        // Delete bank account
        mockMvc.perform(delete("/api/v1/users/me/bank-account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify now GET returns empty
        mockMvc.perform(get("/api/v1/users/me/bank-account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        User refreshed = userRepository.findById(user.getUserId()).orElseThrow();
        assertNull(refreshed.getBankBin());
        assertNull(refreshed.getBankAccountNumber());
        assertNull(refreshed.getBankAccountName());
    }

    @Test
    @DisplayName("USER38: Bank account endpoints enforce authentication and ACTIVE status boundary")
    void testUser38_bankAccountEndpoints_authBoundary() throws Exception {
        // Without auth header
        mockMvc.perform(get("/api/v1/users/me/bank-account"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/users/me/bank-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/users/me/bank-account"))
                .andExpect(status().isUnauthorized());

        // BANNED user
        String bannedUid = "firebase-banned-bank-uid";
        String bannedEmail = "banned.bank@ptit.edu.vn";
        User banned = createSyntheticUser(bannedEmail, UserRole.STUDENT, UserStatus.BANNED);
        createSyntheticIdentity(banned, bannedUid);

        String bannedToken = createSignedToken(bannedUid, bannedEmail, true, "Banned", null);

        mockMvc.perform(get("/api/v1/users/me/bank-account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bannedToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", is("User account is banned.")));
        mockMvc.perform(delete("/api/v1/users/me/bank-account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bannedToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", is("User account is banned.")));
    }
}
