package com.ptiters.marketplace.user;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.ptiters.marketplace.category.CategoryRepository;
import com.ptiters.marketplace.configuration.JwtConfig;
import com.ptiters.marketplace.exception.ApiException;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
import java.util.*;
import java.util.concurrent.*;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserIdentityResolutionIntegrationTest {

    private static final String TEST_PROJECT_ID = "test-marketplace-ptit";
    private static final String TEST_ISSUER = "https://securetoken.google.com/" + TEST_PROJECT_ID;
    private static final String TEST_KID = "test-identity-key-001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuthIdentityRepository userAuthIdentityRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @TestConfiguration
    static class TestIdentityConfig {
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

        @Bean
        public TestIdentityController testIdentityController() {
            return new TestIdentityController();
        }
    }

    @RestController
    @RequestMapping("/api/v1/test/identity")
    static class TestIdentityController {
        @Autowired
        private CurrentUserResolver currentUserResolver;

        @GetMapping("/me")
        public ResponseEntity<Map<String, Object>> getMe() {
            User user = currentUserResolver.getCurrentActiveUser();
            return ResponseEntity.ok(Map.of(
                    "userId", user.getUserId(),
                    "email", user.getEmail(),
                    "status", user.getStatus().name(),
                    "role", user.getRole().name()
            ));
        }

        @GetMapping("/me/access-status")
        public ResponseEntity<Map<String, Object>> getAccessStatus() {
            User user = currentUserResolver.getCurrentUser();
            return ResponseEntity.ok(Map.of(
                    "userId", user.getUserId(),
                    "email", user.getEmail(),
                    "status", user.getStatus().name(),
                    "role", user.getRole().name()
            ));
        }
    }

    @BeforeEach
    void verifyDatabaseSafetyBoundaryAndReset() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT current_database()")) {
            assertTrue(rs.next(), "Database query failed to return current database name");
            String dbName = rs.getString(1);
            assertEquals("marketplace_java_test", dbName,
                    "SAFETY VIOLATION: Identity tests are strictly forbidden outside marketplace_java_test!");
        }

        assertEquals("false", environment.getProperty("spring.flyway.enabled"),
                "Flyway must be disabled during write tests");
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"),
                "Hibernate ddl-auto must remain validate");

        userAuthIdentityRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @AfterEach
    void cleanUpDatabase() {
        userAuthIdentityRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private Jwt createJwt(String sub, String email, Boolean emailVerified, String name, String picture) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("mock-token-" + sub)
                .header("alg", "RS256")
                .header("kid", TEST_KID)
                .header("typ", "JWT")
                .subject(sub)
                .issuer(TEST_ISSUER)
                .audience(List.of(TEST_PROJECT_ID))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
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

        return builder.build();
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
        signedJWT.sign(new RSASSASigner(TestIdentityConfig.KEY_PAIR.getPrivate()));
        return signedJWT.serialize();
    }

    private User createSyntheticUser(String email, UserRole role, UserStatus status) {
        User user = new User();
        user.setEmail(email);
        user.setFullName("Test User " + email);
        user.setRole(role);
        user.setStatus(status);
        user.setPublicId(UUID.randomUUID());
        return userRepository.saveAndFlush(user);
    }

    private UserAuthIdentity createSyntheticIdentity(User user, String providerUid) {
        UserAuthIdentity identity = new UserAuthIdentity();
        identity.setUser(user);
        identity.setProvider(UserAuthIdentity.PROVIDER_FIREBASE);
        identity.setProviderUid(providerUid);
        return userAuthIdentityRepository.saveAndFlush(identity);
    }

    @Test
    @DisplayName("IDENTITY01: Existing identity resolves existing user and updates lastActiveAt / avatarUrl")
    void testResolveUser_ExistingIdentity_SuccessAndUpdatesLastActiveAndAvatar() {
        User user = createSyntheticUser("existing@ptit.edu.vn", UserRole.STUDENT, UserStatus.ACTIVE);
        user.setAvatarUrl("https://old.avatar/pic.png");
        userRepository.saveAndFlush(user);
        createSyntheticIdentity(user, "firebase-existing-uid-01");

        String newAvatar = "https://lh3.googleusercontent.com/new-avatar.png";
        Jwt jwt = createJwt("firebase-existing-uid-01", "existing@ptit.edu.vn", true, "Existing Student", newAvatar);

        User resolved = userService.resolveUser(jwt);

        assertNotNull(resolved);
        assertEquals(user.getUserId(), resolved.getUserId());
        assertEquals("existing@ptit.edu.vn", resolved.getEmail());
        assertEquals(newAvatar, resolved.getAvatarUrl(), "Avatar URL should update when provided in token");
        assertNotNull(resolved.getLastActiveAt(), "lastActiveAt must be populated");
    }

    @Test
    @DisplayName("IDENTITY02: Resolving same identity multiple times is idempotent and produces no duplicates")
    void testResolveUser_Idempotency() {
        String uid = "firebase-idempotent-uid";
        Jwt jwt = createJwt(uid, "idempotent@ptit.edu.vn", true, "Idempotent User", null);

        User firstResolution = userService.resolveUser(jwt);
        User secondResolution = userService.resolveUser(jwt);

        assertEquals(firstResolution.getUserId(), secondResolution.getUserId());
        assertEquals(1, userRepository.count(), "Exactly 1 user row must exist");
        assertEquals(1, userAuthIdentityRepository.count(), "Exactly 1 identity row must exist");
    }

    @Test
    @DisplayName("IDENTITY03: Brand new Firebase UID triggers JIT provisioning with defaults")
    void testResolveUser_BrandNewUser_JITProvisioning() {
        String uid = "firebase-brand-new-uid";
        String email = "brandnew@ptit.edu.vn";
        String name = "Brand New Student";
        Jwt jwt = createJwt(uid, email, true, name, "https://lh3.google.com/avatar.jpg");

        User resolved = userService.resolveUser(jwt);

        assertNotNull(resolved.getUserId());
        assertEquals(email, resolved.getEmail());
        assertEquals(name, resolved.getFullName());
        assertEquals(UserRole.STUDENT, resolved.getRole(), "New users must default to STUDENT");
        assertEquals(UserStatus.ACTIVE, resolved.getStatus(), "New users must default to ACTIVE");
        assertNotNull(resolved.getPublicId(), "publicId UUID must be generated");
        assertFalse(resolved.isShowPhonePublicly(), "showPhonePublicly must default to false");
        assertNotNull(resolved.getLastActiveAt(), "lastActiveAt must be recorded");

        Optional<UserAuthIdentity> identityOpt = userAuthIdentityRepository.findByProviderAndProviderUid(
                UserAuthIdentity.PROVIDER_FIREBASE, uid);
        assertTrue(identityOpt.isPresent(), "UserAuthIdentity must be persisted in PostgreSQL");
        assertEquals(resolved.getUserId(), identityOpt.get().getUser().getUserId());
    }

    @Test
    @DisplayName("IDENTITY04: Untrusted client claims cannot spoof user role, status or publicId")
    void testResolveUser_ClientSpoofingIgnored() {
        String uid = "firebase-spoof-uid";
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .header("kid", TEST_KID)
                .header("typ", "JWT")
                .subject(uid)
                .issuer(TEST_ISSUER)
                .audience(List.of(TEST_PROJECT_ID))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("auth_time", now.getEpochSecond())
                .claim("email", "attacker@ptit.edu.vn")
                .claim("email_verified", true)
                .claim("name", "Attacker")
                .claim("role", "ADMIN")          // Attempt to elevate to ADMIN
                .claim("status", "BANNED")       // Attempt to set status
                .claim("public_id", UUID.randomUUID().toString()) // Attempt custom publicId
                .build();

        User resolved = userService.resolveUser(jwt);

        assertEquals(UserRole.STUDENT, resolved.getRole(), "Privilege escalation must be prevented: role must remain STUDENT");
        assertEquals(UserStatus.ACTIVE, resolved.getStatus(), "Status spoofing must be prevented: status must remain ACTIVE");
    }

    @Test
    @DisplayName("IDENTITY05: Existing user without identity with email_verified=true successfully links account")
    void testResolveUser_ExistingUserWithoutIdentity_EmailVerifiedTrue_AutoLinks() {
        User legacyUser = createSyntheticUser("legacy@ptit.edu.vn", UserRole.STUDENT, UserStatus.ACTIVE);
        String uid = "firebase-legacy-linker-uid";
        Jwt jwt = createJwt(uid, "legacy@ptit.edu.vn", true, "Legacy Linked", null);

        User resolved = userService.resolveUser(jwt);

        assertEquals(legacyUser.getUserId(), resolved.getUserId(), "Must link to the existing user record");
        Optional<UserAuthIdentity> linkedIdentity = userAuthIdentityRepository.findByUser_UserId(legacyUser.getUserId());
        assertTrue(linkedIdentity.isPresent(), "UserAuthIdentity must now exist for the legacy user");
        assertEquals(uid, linkedIdentity.get().getProviderUid());
    }

    @Test
    @DisplayName("IDENTITY06: Existing user without identity with email_verified=false rejects with 400 Bad Request")
    void testResolveUser_ExistingUserWithoutIdentity_EmailVerifiedFalse_RejectsWith400BadRequest() {
        User unverifiedUser = createSyntheticUser("unverified@ptit.edu.vn", UserRole.STUDENT, UserStatus.ACTIVE);
        String uid = "firebase-unverified-uid";
        Jwt jwt = createJwt(uid, "unverified@ptit.edu.vn", false, "Unverified Hacker", null);

        ApiException ex = assertThrows(ApiException.class, () -> userService.resolveUser(jwt));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Cannot link account: email is not verified.", ex.getMessage());

        assertTrue(userAuthIdentityRepository.findByUser_UserId(unverifiedUser.getUserId()).isEmpty(),
                "Identity must NOT be linked when email is unverified");
    }

    @Test
    @DisplayName("IDENTITY07: Anti-takeover: Token with email matching user that ALREADY has another identity rejects with 409 Conflict")
    void testResolveUser_AntiTakeover_ExistingUserWithDifferentIdentity_RejectsWith409Conflict() {
        User legitUser = createSyntheticUser("victim@ptit.edu.vn", UserRole.STUDENT, UserStatus.ACTIVE);
        createSyntheticIdentity(legitUser, "legit-firebase-uid");

        // Attacker claims same email with different Firebase UID
        String attackerUid = "attacker-firebase-uid";
        Jwt jwt = createJwt(attackerUid, "victim@ptit.edu.vn", true, "Attacker", null);

        ApiException ex = assertThrows(ApiException.class, () -> userService.resolveUser(jwt));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Account already linked to a different provider identity.", ex.getMessage());

        // Legitimate identity remains intact
        UserAuthIdentity originalIdentity = userAuthIdentityRepository.findByUser_UserId(legitUser.getUserId()).orElseThrow();
        assertEquals("legit-firebase-uid", originalIdentity.getProviderUid());
    }

    @Test
    @DisplayName("IDENTITY08: Missing email claim falls back to {providerUid}@firebase.user")
    void testResolveUser_MissingEmail_FallbacksToProviderEmail() {
        String uid = "phone-uid-998877";
        Jwt jwt = createJwt(uid, null, false, null, null);

        User resolved = userService.resolveUser(jwt);

        assertEquals("phone-uid-998877@firebase.user", resolved.getEmail());
        assertEquals("phone-uid-998877", resolved.getFullName());
    }

    @Test
    @DisplayName("IDENTITY09: Missing name claim falls back to email prefix before @")
    void testResolveUser_MissingName_FallbacksToEmailPrefix() {
        String uid = "noname-uid-112233";
        Jwt jwt = createJwt(uid, "nguyen.van.b@ptit.edu.vn", true, null, null);

        User resolved = userService.resolveUser(jwt);

        assertEquals("nguyen.van.b", resolved.getFullName());
    }

    @Test
    @DisplayName("IDENTITY10: Active user passes requireActiveUser")
    void testRequireActiveUser_ActiveUser_Passes() {
        User activeUser = createSyntheticUser("active@ptit.edu.vn", UserRole.STUDENT, UserStatus.ACTIVE);
        User result = userService.requireActiveUser(activeUser);
        assertSame(activeUser, result);
    }

    @Test
    @DisplayName("IDENTITY11: Banned user fails requireActiveUser with 403 Forbidden 'User account is banned.'")
    void testRequireActiveUser_BannedUser_Throws403Forbidden() {
        User bannedUser = createSyntheticUser("banned@ptit.edu.vn", UserRole.STUDENT, UserStatus.BANNED);
        ApiException ex = assertThrows(ApiException.class, () -> userService.requireActiveUser(bannedUser));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("User account is banned.", ex.getMessage());
    }

    @Test
    @DisplayName("IDENTITY12: Inactive user fails requireActiveUser with 403 Forbidden 'User account is inactive.'")
    void testRequireActiveUser_InactiveUser_Throws403Forbidden() {
        User inactiveUser = createSyntheticUser("inactive@ptit.edu.vn", UserRole.STUDENT, UserStatus.INACTIVE);
        ApiException ex = assertThrows(ApiException.class, () -> userService.requireActiveUser(inactiveUser));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("User account is inactive.", ex.getMessage());
    }

    @Test
    @DisplayName("IDENTITY13: Concurrency / Race Condition: 10 threads concurrently resolving same brand-new user succeeds idempotently")
    void testConcurrentProvisioning_SameUser_RaceConditionHandled() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<User>> futures = new ArrayList<>();

        String concurrentUid = "concurrent-uid-" + UUID.randomUUID();
        String concurrentEmail = "concurrent_" + UUID.randomUUID() + "@ptit.edu.vn";

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                Jwt jwt = createJwt(concurrentUid, concurrentEmail, true, "Concurrent Student", null);
                return userService.resolveUser(jwt);
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        List<User> results = new ArrayList<>();
        for (Future<User> future : futures) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();

        assertFalse(results.isEmpty());
        Long expectedUserId = results.get(0).getUserId();
        assertNotNull(expectedUserId);

        for (User u : results) {
            assertEquals(expectedUserId, u.getUserId(), "All concurrent threads must resolve to the identical User ID");
        }

        // Verify DB integrity: exactly 1 user row and 1 identity row
        assertEquals(1, userRepository.count());
        assertEquals(1, userAuthIdentityRepository.count());
    }

    @Test
    @DisplayName("IDENTITY14: HTTP MockMvc - Missing Authorization header returns 401 with standard JSON detail")
    void testHttpMockMvc_MissingAuthHeader_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/test/identity/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.detail", is("Missing or invalid Authorization header.")));
    }

    @Test
    @DisplayName("IDENTITY15: HTTP MockMvc - Active user successfully authenticates and returns 200 OK")
    void testHttpMockMvc_ActiveUser_Returns200() throws Exception {
        String token = createSignedToken("firebase-http-active-uid", "active.http@ptit.edu.vn", true, "Active Student", null);

        mockMvc.perform(get("/api/v1/test/identity/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("active.http@ptit.edu.vn")))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.role", is("STUDENT")));
    }

    @Test
    @DisplayName("IDENTITY16: HTTP MockMvc - Banned user gets 403 on /me, but gets 200 on /me/access-status")
    void testHttpMockMvc_BannedUser_MeReturns403_AccessStatusReturns200() throws Exception {
        String uid = "firebase-banned-uid";
        String email = "banned.http@ptit.edu.vn";
        User bannedUser = createSyntheticUser(email, UserRole.STUDENT, UserStatus.BANNED);
        createSyntheticIdentity(bannedUser, uid);

        String token = createSignedToken(uid, email, true, "Banned Student", null);

        // /me requires active user -> 403
        mockMvc.perform(get("/api/v1/test/identity/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", is("User account is banned.")));

        // /me/access-status accepts banned user -> 200 OK
        mockMvc.perform(get("/api/v1/test/identity/me/access-status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("BANNED")))
                .andExpect(jsonPath("$.email", is(email)));
    }

    @Test
    @DisplayName("IDENTITY17: HTTP MockMvc - Inactive user gets 403 on /me, but gets 200 on /me/access-status")
    void testHttpMockMvc_InactiveUser_MeReturns403_AccessStatusReturns200() throws Exception {
        String uid = "firebase-inactive-uid";
        String email = "inactive.http@ptit.edu.vn";
        User inactiveUser = createSyntheticUser(email, UserRole.STUDENT, UserStatus.INACTIVE);
        createSyntheticIdentity(inactiveUser, uid);

        String token = createSignedToken(uid, email, true, "Inactive Student", null);

        // /me requires active user -> 403
        mockMvc.perform(get("/api/v1/test/identity/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", is("User account is inactive.")));

        // /me/access-status accepts inactive user -> 200 OK
        mockMvc.perform(get("/api/v1/test/identity/me/access-status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("INACTIVE")))
                .andExpect(jsonPath("$.email", is(email)));
    }

    @Test
    @DisplayName("IDENTITY18: Public Category endpoint does not trigger user provisioning or writes")
    void testPublicCategoryAccess_DoesNotTriggerUserProvisioningOrWrites() throws Exception {
        long initialUsers = userRepository.count();
        long initialIdentities = userAuthIdentityRepository.count();

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk());

        assertEquals(initialUsers, userRepository.count(), "Category read must not alter user table");
        assertEquals(initialIdentities, userAuthIdentityRepository.count(), "Category read must not alter identity table");
    }
}
