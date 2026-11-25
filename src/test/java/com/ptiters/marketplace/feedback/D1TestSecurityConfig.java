package com.ptiters.marketplace.feedback;

import com.ptiters.marketplace.product.ProductTestFixtures;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@TestConfiguration
public class D1TestSecurityConfig {

    @Bean
    @Primary
    public JwtDecoder d1TestJwtDecoder() {
        return ProductTestFixtures.createTestJwtDecoder();
    }
}
