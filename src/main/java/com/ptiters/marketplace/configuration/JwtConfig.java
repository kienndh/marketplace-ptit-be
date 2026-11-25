package com.ptiters.marketplace.configuration;

import com.ptiters.marketplace.security.FirebaseJwtCustomValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import java.time.Duration;
import java.util.List;

@Configuration
public class JwtConfig {

    @Value("${firebase.project-id}")
    private String projectId;

    @Value("${firebase.jwk-set-uri}")
    private String jwkSetUri;

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        jwtDecoder.setJwtValidator(createFirebaseJwtValidator(projectId));
        return jwtDecoder;
    }

    public static OAuth2TokenValidator<Jwt> createFirebaseJwtValidator(String projectId) {
        String issuer = "https://securetoken.google.com/" + projectId;
        OAuth2TokenValidator<Jwt> timestampValidator = new JwtTimestampValidator(Duration.ofSeconds(60));
        OAuth2TokenValidator<Jwt> issuerValidator = new JwtIssuerValidator(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                aud -> aud != null && aud.size() == 1 && projectId.equals(aud.getFirst())
        );
        OAuth2TokenValidator<Jwt> customValidator = new FirebaseJwtCustomValidator();

        return new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                issuerValidator,
                audienceValidator,
                customValidator
        );
    }
}
