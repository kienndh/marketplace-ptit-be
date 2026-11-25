package com.ptiters.marketplace.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.ptiters.marketplace.configuration.JwtConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FirebaseJwtValidatorTest {

    private static final String PROJECT_ID = "test-marketplace-ptit";
    private static final String ISSUER = "https://securetoken.google.com/" + PROJECT_ID;
    private static final String TEST_KID = "test-key-id-001";

    private static KeyPair keyPair;
    private static KeyPair wrongKeyPair;
    private static NimbusJwtDecoder jwtDecoder;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        keyPair = kpg.generateKeyPair();
        wrongKeyPair = kpg.generateKeyPair();

        jwtDecoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
        jwtDecoder.setJwtValidator(JwtConfig.createFirebaseJwtValidator(PROJECT_ID));
    }

    private String buildToken(
            RSAPrivateKey privateKey,
            String kid,
            String sub,
            String iss,
            Object aud,
            Instant iat,
            Instant exp,
            Object authTime,
            Map<String, Object> additionalClaims
    ) throws Exception {
        JWSHeader.Builder headerBuilder = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT);
        if (kid != null) {
            headerBuilder.keyID(kid);
        }
        JWSHeader header = headerBuilder.build();

        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        if (sub != null) builder.subject(sub);
        if (iss != null) builder.issuer(iss);
        if (aud instanceof String s) builder.audience(s);
        else if (aud instanceof List<?> l) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) l;
            builder.audience(list);
        }
        if (iat != null) builder.issueTime(Date.from(iat));
        if (exp != null) builder.expirationTime(Date.from(exp));
        if (authTime instanceof Instant inst) builder.claim("auth_time", inst.getEpochSecond());
        else if (authTime != null) builder.claim("auth_time", authTime);

        if (additionalClaims != null) {
            additionalClaims.forEach(builder::claim);
        }

        SignedJWT signedJWT = new SignedJWT(header, builder.build());
        signedJWT.sign(new RSASSASigner(privateKey));
        return signedJWT.serialize();
    }

    @Test
    @DisplayName("N: Valid RS256 token with all Firebase claims passes")
    void testValidTokenPasses() throws Exception {
        Instant now = Instant.now();
        String token = buildToken(
                (RSAPrivateKey) keyPair.getPrivate(),
                TEST_KID,
                "firebase-user-12345",
                ISSUER,
                PROJECT_ID,
                now.minus(5, ChronoUnit.MINUTES),
                now.plus(1, ChronoUnit.HOURS),
                now.minus(5, ChronoUnit.MINUTES),
                null
        );

        Jwt jwt = jwtDecoder.decode(token);
        assertNotNull(jwt);
        assertEquals("firebase-user-12345", jwt.getSubject());
        assertEquals(ISSUER, jwt.getIssuer().toString());
        assertEquals(List.of(PROJECT_ID), jwt.getAudience());
        assertEquals(TEST_KID, jwt.getHeaders().get("kid"));
    }

    @Test
    @DisplayName("A1: Missing exp claim rejected")
    void testMissingExpRejected() throws Exception {
        Instant now = Instant.now();
        String token = buildToken(
                (RSAPrivateKey) keyPair.getPrivate(),
                TEST_KID,
                "firebase-user-12345",
                ISSUER,
                PROJECT_ID,
                now.minus(5, ChronoUnit.MINUTES),
                null, // missing exp
                now.minus(5, ChronoUnit.MINUTES),
                null
        );

        assertThrows(JwtValidationException.class, () -> jwtDecoder.decode(token));
    }

    @Test
    @DisplayName("A2: Expired token rejected")
    void testExpiredTokenRejected() throws Exception {
        Instant now = Instant.now();
        String token = buildToken(
                (RSAPrivateKey) keyPair.getPrivate(),
                TEST_KID,
                "firebase-user-12345",
                ISSUER,
                PROJECT_ID,
                now.minus(2, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.HOURS),
                now.minus(2, ChronoUnit.HOURS),
                null
        );

        assertThrows(JwtValidationException.class, () -> jwtDecoder.decode(token));
    }

    @Test
    @DisplayName("B: Missing iat claim rejected")
    void testMissingIatRejected() throws Exception {
        Instant now = Instant.now();
        String token = buildToken(
                (RSAPrivateKey) keyPair.getPrivate(),
                TEST_KID,
                "firebase-user-12345",
                ISSUER,
                PROJECT_ID,
                null, // missing iat
                now.plus(1, ChronoUnit.HOURS),
                now.minus(5, ChronoUnit.MINUTES),
                null
        );

        assertThrows(JwtValidationException.class, () -> jwtDecoder.decode(token));
    }

    @Test
    @DisplayName("F: Future iat rejected")
    void testFutureIatRejected() throws Exception {
        Instant now = Instant.now();
        String token = buildToken(
                (RSAPrivateKey) keyPair.getPrivate(),
                TEST_KID,
                "firebase-user-12345",
                ISSUER,
                PROJECT_ID,
                now.plus(1, ChronoUnit.HOURS), // Future iat
                now.plus(2, ChronoUnit.HOURS),
                now.minus(5, ChronoUnit.MINUTES),
                null
        );

        assertThrows(JwtValidationException.class, () -> jwtDecoder.decode(token));
    }

    @Test
    @DisplayName("C: Missing auth_time rejected")
    void testMissingAuthTimeRejected() throws Exception {
        Instant now = Instant.now();
        String token = buildToken(
                (RSAPrivateKey) keyPair.getPrivate(),
                TEST_KID,
                "firebase-user-12345",
                ISSUER,
                PROJECT_ID,
                now.minus(5, ChronoUnit.MINUTES),
                now.plus(1, ChronoUnit.HOURS),
                null, // Missing auth_time
                null
        );

        assertThrows(JwtValidationException.class, () -> jwtDecoder.decode(token));
    }

    @Test
    @DisplayName("D: Wrong data type for auth_time rejected (string or boolean)")
    void testInvalidAuthTimeDataTypeRejected() throws Exception {
        Instant now = Instant.now();
        // auth_time as non-numeric string
        String tokenStringAuthTime = buildToken(
                (RSAPrivateKey) keyPair.getPrivate(),
                TEST_KID,
                "firebase-user-12345",
                ISSUER,
                PROJECT_ID,
                now.minus(5, ChronoUnit.MINUTES),
                now.plus(1, ChronoUnit.HOURS),
                "not-a-timestamp",
                null
        );
        assertThrows(JwtValidationException.class, () -> jwtDecoder.decode(tokenStringAuthTime));

        // auth_time as boolean
        String tokenBoolAuthTime = buildToken(
                (RSAPrivateKey) keyPair.getPrivate(),
                TEST_KID,
                "firebase-user-12345",
                ISSUER,
                PROJECT_ID,
                now.minus(5, ChronoUnit.MINUTES),
                now.plus(1, ChronoUnit.HOURS),
                true,
                null
        );
        assertThrows(JwtValidationException.class, () -> jwtDecoder.decode(tokenBoolAuthTime));
    }

    @Test
    @DisplayName("E: Future auth_time rejected")
    void testFutureAuthTimeRejected() throws Exception {
        Instant now = Instant.now();
        String token = buildToken(
                (RSAPrivateKey) keyPair.getPrivate(),
                TEST_KID,
                "firebase-user-12345",
                ISSUER,
                PROJECT_ID,
                now.minus(5, ChronoUnit.MINUTES),
                now.plus(1, ChronoUnit.HOURS),
                now.plus(2, ChronoUnit.HOURS), // Future auth_time
                null
        );

        assertThrows(JwtValidationException.class, () -> jwtDecoder.decode(token));
    }

    @Test
    @DisplayName("G1: Missing sub (null) rejected")
    void testMissingSubRejected() throws Exception {
        Instant now = Instant.now();
        String token = buildToken(
                (RSAPrivateKey) keyPair.getPrivate(),
                TEST_KID,
                null, // Missing sub
                ISSUER,
                PROJECT_ID,
                now.minus(5, ChronoUnit.MINUTES),
                now.plus(1, ChronoUnit.HOURS),
                now.minus(5, ChronoUnit.MINUTES),
                null
        );

        assertThrows(JwtValidationException.class, () -> jwtDecoder.decode(token));
    }

    @Test
    @DisplayName("G2: Empty or whitespace sub rejected")
    void testEmptyOrWhitespaceSubRejected() throws Exception {
        Instant now = Instant.now();
        // Empty sub
        String tokenEmpty = buildToken(
                (RSAPrivateKey) keyPair.getPrivate(),
                TEST_KID,
                "",
                ISSUER,
                PROJECT_ID,
                now.minus(5, ChronoUnit.MINUTES),
                now.plus(1, ChronoUnit.HOURS),
                now.minus(5, ChronoUnit.MINUTES),
                null
        );
        assertThrows(JwtValidationException.class, () -> jwtDecoder.decode(tokenEmpty));

        // Whitespace sub
        String tokenWhitespace = buildToken(
                (RSAPrivateKey) keyPair.getPrivate(),
                TEST_KID,
                "   \t\n  ",
                ISSUER,
                PROJECT_ID,
                now.minus(5, ChronoUnit.MINUTES),
                now.plus(1, ChronoUnit.HOURS),
                now.minus(5, ChronoUnit.MINUTES),
                null
        );
        assertThrows(JwtValidationException.class, () -> jwtDecoder.decode(tokenWhitespace));
    }

    @Test
    @DisplayName("H: Missing kid header rejected")
    void testMissingKidRejected() throws Exception {
        Instant now = Instant.now();
        String token = buildToken(
                (RSAPrivateKey) keyPair.getPrivate(),
                null, // Missing kid
                "firebase-user-12345",
                ISSUER,
                PROJECT_ID,
                now.minus(5, ChronoUnit.MINUTES),
                now.plus(1, ChronoUnit.HOURS),
                now.minus(5, ChronoUnit.MINUTES),
                null
        );

        assertThrows(JwtValidationException.class, () -> jwtDecoder.decode(token));
    }

    @Test
    @DisplayName("I: Blank kid header rejected")
    void testBlankKidRejected() throws Exception {
        Instant now = Instant.now();
        String token = buildToken(
                (RSAPrivateKey) keyPair.getPrivate(),
                "   ", // Blank kid
                "firebase-user-12345",
                ISSUER,
                PROJECT_ID,
                now.minus(5, ChronoUnit.MINUTES),
                now.plus(1, ChronoUnit.HOURS),
                now.minus(5, ChronoUnit.MINUTES),
                null
        );

        assertThrows(JwtValidationException.class, () -> jwtDecoder.decode(token));
    }

    @Test
    @DisplayName("J: Unsupported signing algorithm (e.g. HS256) rejected")
    void testUnsupportedAlgorithmRejected() throws Exception {
        Instant now = Instant.now();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256)
                .keyID(TEST_KID)
                .type(JOSEObjectType.JWT)
                .build();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("firebase-user-12345")
                .issuer(ISSUER)
                .audience(PROJECT_ID)
                .issueTime(Date.from(now.minus(5, ChronoUnit.MINUTES)))
                .expirationTime(Date.from(now.plus(1, ChronoUnit.HOURS)))
                .claim("auth_time", now.minus(5, ChronoUnit.MINUTES).getEpochSecond())
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(new MACSigner("01234567890123456789012345678901".getBytes()));
        String hs256Token = signedJWT.serialize();

        assertThrows(BadJwtException.class, () -> jwtDecoder.decode(hs256Token));
    }

    @Test
    @DisplayName("K: Invalid RSA signature / signed by wrong key rejected")
    void testWrongKeySignatureRejected() throws Exception {
        Instant now = Instant.now();
        String token = buildToken(
                (RSAPrivateKey) wrongKeyPair.getPrivate(),
                TEST_KID,
                "firebase-user-12345",
                ISSUER,
                PROJECT_ID,
                now.minus(5, ChronoUnit.MINUTES),
                now.plus(1, ChronoUnit.HOURS),
                now.minus(5, ChronoUnit.MINUTES),
                null
        );

        assertThrows(BadJwtException.class, () -> jwtDecoder.decode(token));
    }

    @Test
    @DisplayName("L: Wrong Firebase issuer rejected")
    void testWrongIssuerRejected() throws Exception {
        Instant now = Instant.now();
        String token = buildToken(
                (RSAPrivateKey) keyPair.getPrivate(),
                TEST_KID,
                "firebase-user-12345",
                "https://securetoken.google.com/malicious-project",
                PROJECT_ID,
                now.minus(5, ChronoUnit.MINUTES),
                now.plus(1, ChronoUnit.HOURS),
                now.minus(5, ChronoUnit.MINUTES),
                null
        );

        assertThrows(JwtValidationException.class, () -> jwtDecoder.decode(token));
    }

    @Test
    @DisplayName("M1: Wrong audience (single other project) rejected")
    void testWrongAudienceRejected() throws Exception {
        Instant now = Instant.now();
        String token = buildToken(
                (RSAPrivateKey) keyPair.getPrivate(),
                TEST_KID,
                "firebase-user-12345",
                ISSUER,
                "wrong-audience-project",
                now.minus(5, ChronoUnit.MINUTES),
                now.plus(1, ChronoUnit.HOURS),
                now.minus(5, ChronoUnit.MINUTES),
                null
        );

        assertThrows(JwtValidationException.class, () -> jwtDecoder.decode(token));
    }

    @Test
    @DisplayName("M2: Missing or empty audience rejected")
    void testMissingOrEmptyAudienceRejected() throws Exception {
        Instant now = Instant.now();
        // Null audience
        String tokenNullAud = buildToken(
                (RSAPrivateKey) keyPair.getPrivate(),
                TEST_KID,
                "firebase-user-12345",
                ISSUER,
                null,
                now.minus(5, ChronoUnit.MINUTES),
                now.plus(1, ChronoUnit.HOURS),
                now.minus(5, ChronoUnit.MINUTES),
                null
        );
        assertThrows(JwtValidationException.class, () -> jwtDecoder.decode(tokenNullAud));

        // Empty list audience
        String tokenEmptyAud = buildToken(
                (RSAPrivateKey) keyPair.getPrivate(),
                TEST_KID,
                "firebase-user-12345",
                ISSUER,
                Collections.emptyList(),
                now.minus(5, ChronoUnit.MINUTES),
                now.plus(1, ChronoUnit.HOURS),
                now.minus(5, ChronoUnit.MINUTES),
                null
        );
        assertThrows(JwtValidationException.class, () -> jwtDecoder.decode(tokenEmptyAud));
    }

    @Test
    @DisplayName("M3: Multiple audiences rejected (even if containing configured project ID)")
    void testMultipleAudiencesRejected() throws Exception {
        Instant now = Instant.now();
        // Token has multiple audiences: configured project ID and an extraneous audience
        String token = buildToken(
                (RSAPrivateKey) keyPair.getPrivate(),
                TEST_KID,
                "firebase-user-12345",
                ISSUER,
                List.of(PROJECT_ID, "attacker-third-party-service"),
                now.minus(5, ChronoUnit.MINUTES),
                now.plus(1, ChronoUnit.HOURS),
                now.minus(5, ChronoUnit.MINUTES),
                null
        );

        assertThrows(JwtValidationException.class, () -> jwtDecoder.decode(token));
    }

    @Test
    @DisplayName("LM: Token for completely different Firebase project rejected")
    void testDifferentFirebaseProjectRejected() throws Exception {
        Instant now = Instant.now();
        String otherProject = "other-firebase-project";
        String token = buildToken(
                (RSAPrivateKey) keyPair.getPrivate(),
                TEST_KID,
                "firebase-user-12345",
                "https://securetoken.google.com/" + otherProject,
                otherProject,
                now.minus(5, ChronoUnit.MINUTES),
                now.plus(1, ChronoUnit.HOURS),
                now.minus(5, ChronoUnit.MINUTES),
                null
        );

        assertThrows(JwtValidationException.class, () -> jwtDecoder.decode(token));
    }
}
