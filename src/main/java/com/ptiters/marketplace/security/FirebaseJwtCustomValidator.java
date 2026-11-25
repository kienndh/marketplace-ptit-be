package com.ptiters.marketplace.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

public class FirebaseJwtCustomValidator implements OAuth2TokenValidator<Jwt> {

    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        // 1. kid (Key ID in JWS header) must be non-null and not blank
        Object kid = jwt.getHeaders().get("kid");
        if (kid == null || kid.toString().trim().isEmpty()) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_TOKEN,
                    "kid header is required and must not be blank",
                    null
            ));
        }

        // 2. sub (Subject / Firebase UID) must be non-null and not blank
        String sub = jwt.getSubject();
        if (sub == null || sub.trim().isEmpty()) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_TOKEN,
                    "sub (Subject) claim must be non-empty",
                    null
            ));
        }

        // 3. exp (Expiration Time) must be present
        if (jwt.getExpiresAt() == null) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_TOKEN,
                    "exp claim is required",
                    null
            ));
        }

        // 4. iat (Issued At) must be present and not in the future (with clock skew)
        Instant iat = jwt.getIssuedAt();
        if (iat == null) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_TOKEN,
                    "iat claim is required",
                    null
            ));
        }
        if (iat.isAfter(Instant.now().plus(CLOCK_SKEW))) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_TOKEN,
                    "iat is in the future",
                    null
            ));
        }

        // 5. auth_time must be present, valid numeric timestamp, and not in the future (with clock skew)
        Object authTimeClaim = jwt.getClaim("auth_time");
        if (authTimeClaim == null) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_TOKEN,
                    "Missing auth_time claim",
                    null
            ));
        }

        Instant authTimeInstant;
        if (authTimeClaim instanceof Instant instant) {
            authTimeInstant = instant;
        } else if (authTimeClaim instanceof Number number) {
            authTimeInstant = Instant.ofEpochSecond(number.longValue());
        } else if (authTimeClaim instanceof Date date) {
            authTimeInstant = date.toInstant();
        } else {
            try {
                authTimeInstant = Instant.ofEpochSecond(Long.parseLong(authTimeClaim.toString()));
            } catch (Exception e) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        OAuth2ErrorCodes.INVALID_TOKEN,
                        "Invalid auth_time format: must be numeric timestamp",
                        null
                ));
            }
        }

        if (authTimeInstant.isAfter(Instant.now().plus(CLOCK_SKEW))) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_TOKEN,
                    "auth_time is in the future",
                    null
            ));
        }

        return OAuth2TokenValidatorResult.success();
    }
}
