package com.ptiters.marketplace.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.ptiters.marketplace.category.Category;
import com.ptiters.marketplace.category.CategoryRepository;
import com.ptiters.marketplace.category.CategoryTestFixtures;
import com.ptiters.marketplace.configuration.JwtConfig;
import com.ptiters.marketplace.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTest {

    private static final String TEST_PROJECT_ID = "test-marketplace-ptit";
    private static final String TEST_ISSUER = "https://securetoken.google.com/" + TEST_PROJECT_ID;
    private static final String TEST_KID = "test-key-id-001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @TestConfiguration
    static class TestSecurityConfig {
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
        public TestSecurityController testSecurityController() {
            return new TestSecurityController();
        }
    }

    @RestController
    @RequestMapping("/api/v1/test")
    static class TestSecurityController {

        @GetMapping("/protected")
        public ResponseEntity<Map<String, Object>> protectedEndpoint(@AuthenticationPrincipal Jwt jwt) {
            return ResponseEntity.ok(Map.of(
                    "message", "authenticated",
                    "uid", jwt.getSubject()
            ));
        }

        @GetMapping("/denied")
        public ResponseEntity<Map<String, Object>> deniedEndpoint() {
            // Emulates an authorization decision failure in service layer or Spring Security
            throw new AccessDeniedException("Operation not permitted for your user role.");
        }
    }

    @BeforeEach
    void verifyDatabaseSafetyBoundary() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT current_database()")) {
            assertTrue(rs.next(), "Database query failed to return current database name");
            String dbName = rs.getString(1);
            assertEquals("marketplace_java_test", dbName,
                    "SAFETY VIOLATION: Security integration tests are strictly forbidden outside marketplace_java_test!");
        }

        assertEquals("false", environment.getProperty("spring.flyway.enabled"),
                "Flyway must be disabled during write tests");
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"),
                "Hibernate ddl-auto must remain validate");
    }

    private String createValidToken(String sub) throws Exception {
        Instant now = Instant.now();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(TEST_KID)
                .type(JOSEObjectType.JWT)
                .build();

        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject(sub)
                .issuer(TEST_ISSUER)
                .audience(TEST_PROJECT_ID)
                .issueTime(Date.from(now.minusSeconds(60)))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .claim("auth_time", now.minusSeconds(60).getEpochSecond());

        SignedJWT signedJWT = new SignedJWT(header, builder.build());
        signedJWT.sign(new RSASSASigner((RSAPrivateKey) TestSecurityConfig.KEY_PAIR.getPrivate()));
        return signedJWT.serialize();
    }

    private String createExpiredToken(String sub) throws Exception {
        Instant now = Instant.now();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(TEST_KID)
                .type(JOSEObjectType.JWT)
                .build();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(sub)
                .issuer(TEST_ISSUER)
                .audience(TEST_PROJECT_ID)
                .issueTime(Date.from(now.minusSeconds(7200)))
                .expirationTime(Date.from(now.minusSeconds(3600)))
                .claim("auth_time", now.minusSeconds(7200).getEpochSecond())
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(new RSASSASigner((RSAPrivateKey) TestSecurityConfig.KEY_PAIR.getPrivate()));
        return signedJWT.serialize();
    }

    // =========================================================================
    // SECTION 3: ANONYMOUS ROUTE COLLISION VERIFICATION MATRIX
    // =========================================================================

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/users/me",
            "/api/v1/users/me/access-status",
            "/api/v1/users/me/bank-account",
            "/api/v1/users/me/anything",
            "/api/v1/users/me/",
            "/api/v1/users/search",
            "/api/v1/users/invalid-non-uuid"
    })
    @DisplayName("SEC-COLLISION: Protected user routes MUST return 401 Unauthorized when accessed anonymously")
    void testProtectedUserRoutesRequireAuthentication(String route) throws Exception {
        mockMvc.perform(get(route)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.detail", is("Missing or invalid Authorization header.")));
    }

    @Test
    @DisplayName("SEC-PUBLIC-01: GET /api/v1/users/banks is PUBLIC (dispatched to MVC, returns 200 OK, not challenged with 401)")
    void testUsersBanksIsPublic() throws Exception {
        // Implemented in UserController, returns 200 OK without requiring authentication
        mockMvc.perform(get("/api/v1/users/banks")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    @DisplayName("SEC-PUBLIC-02: GET /api/v1/users/{validPublicUuid} is PUBLIC (not challenged with 401)")
    void testUsersPublicProfileIsPublic() throws Exception {
        String validUuid = UUID.randomUUID().toString();
        mockMvc.perform(get("/api/v1/users/{public_id}", validUuid)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound()) // 404 shows dispatch reached MVC, NOT 401
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    @DisplayName("SEC-PUBLIC-03: GET /api/v1/users/{validPublicUuid}/sold-products is PUBLIC (not challenged with 401)")
    void testUsersSoldProductsIsPublic() throws Exception {
        String validUuid = UUID.randomUUID().toString();
        mockMvc.perform(get("/api/v1/users/{public_id}/sold-products", validUuid)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound()) // 404 shows dispatch reached MVC, NOT 401
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    @DisplayName("SEC-PUBLIC-04: GET /api/v1/categories returns 200 OK without token")
    void testCategoriesListPublicAccess() throws Exception {
        mockMvc.perform(get("/api/v1/categories")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("SEC-PUBLIC-05: GET /api/v1/categories/{id} returns 200 OK without token")
    void testCategoryDetailPublicAccess() throws Exception {
        Category cat = CategoryTestFixtures.createSyntheticCategory("Public Electronics");
        categoryRepository.save(cat);
        entityManager.flush();

        mockMvc.perform(get("/api/v1/categories/{id}", cat.getCategoryId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.category_id", is(cat.getCategoryId().intValue())))
                .andExpect(jsonPath("$.name", is(cat.getName())));
    }

    // =========================================================================
    // SECTION 4: AUTHENTICATION AND ERROR RESPONSES
    // =========================================================================

    @Test
    @DisplayName("SEC-AUTH-01: Protected endpoint with malformed Bearer token returns 401 JSON")
    void testProtectedEndpointWithMalformedTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.detail", is("Missing or invalid Authorization header.")));
    }

    @Test
    @DisplayName("SEC-AUTH-02: Protected endpoint with expired synthetic JWT returns 401 JSON")
    void testProtectedEndpointWithExpiredTokenReturns401() throws Exception {
        String expiredToken = createExpiredToken("expired-user-1");

        mockMvc.perform(get("/api/v1/test/protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.detail", is("Missing or invalid Authorization header.")));
    }

    @Test
    @DisplayName("SEC-AUTH-03: Protected endpoint with valid synthetic JWT returns 200 OK")
    void testProtectedEndpointWithValidTokenReturns200() throws Exception {
        String validToken = createValidToken("firebase-user-abc-123");

        mockMvc.perform(get("/api/v1/test/protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message", is("authenticated")))
                .andExpect(jsonPath("$.uid", is("firebase-user-abc-123")));
    }

    @Test
    @DisplayName("SEC-AUTH-04: Client-supplied query params or body trying to spoof uid are ignored")
    void testClientSpoofingAttemptIgnored() throws Exception {
        String validToken = createValidToken("legitimate-user-789");

        mockMvc.perform(get("/api/v1/test/protected")
                        .param("user_id", "admin-spoofed")
                        .param("role", "admin")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid", is("legitimate-user-789")));
    }

    @Test
    @DisplayName("SEC-AUTH-05: RestAccessDeniedHandler handles AccessDeniedException with HTTP 403 JSON")
    void testRestAccessDeniedHandler() throws Exception {
        String validToken = createValidToken("legitimate-user-789");

        mockMvc.perform(get("/api/v1/test/denied")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.detail", is("Operation not permitted for your user role.")));
    }

    @Test
    @DisplayName("SEC-AUTH-06: No sensitive token details, stack traces or class names leaked in 401 responses")
    void testNoSensitiveDetailsLeaked() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-garbage-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.detail", is("Missing or invalid Authorization header.")));
    }

    // =========================================================================
    // SECTION 5: CORS AND PREFLIGHT
    // =========================================================================

    @Test
    @DisplayName("SEC-CORS-01: Preflight OPTIONS request from trusted origin returns 200 OK")
    void testPreflightOptionsFromTrustedOriginReturns200() throws Exception {
        mockMvc.perform(options("/api/v1/categories")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    @DisplayName("SEC-CORS-02: Preflight OPTIONS request from untrusted origin does NOT grant CORS headers")
    void testPreflightOptionsFromUntrustedOriginRejected() throws Exception {
        mockMvc.perform(options("/api/v1/categories")
                        .header("Origin", "http://malicious-site.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // =========================================================================
    // SECTION 6: CATEGORY CONTRACT REGRESSION
    // =========================================================================

    @Test
    @DisplayName("SEC-CAT-01: Category 404 behavior intact through security filter chain")
    void testCategory404Intact() throws Exception {
        mockMvc.perform(get("/api/v1/categories/{id}", 999999)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.detail", is("Category not found.")));
    }

    @Test
    @DisplayName("SEC-CAT-02: Category 422 behavior intact through security filter chain")
    void testCategory422Intact() throws Exception {
        mockMvc.perform(get("/api/v1/categories/{id}", "not-an-integer")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.detail", is("Invalid value for request parameter.")));
    }

    // =========================================================================
    // SECTION 7: STATELESSNESS & DB SAFETY
    // =========================================================================

    @Test
    @DisplayName("SEC-DB-01: No DB writes during token verification (pure stateless JWT verification)")
    void testNoDbWritesDuringTokenVerification() throws Exception {
        long userCountBefore = userRepository.count();

        String validToken = createValidToken("stateless-user-100");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/test/protected")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        long userCountAfter = userRepository.count();
        assertEquals(userCountBefore, userCountAfter, "Stateless token verification must NOT write any rows to the database!");
    }
}
