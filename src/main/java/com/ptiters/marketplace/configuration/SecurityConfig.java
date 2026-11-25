package com.ptiters.marketplace.configuration;

import com.ptiters.marketplace.security.RestAccessDeniedHandler;
import com.ptiters.marketplace.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(authorize -> authorize
                // 1. Preflight CORS requests are always permitted
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // ====== PHÂN VÙNG TRACK: mỗi track CHỈ thêm rule trong khối của mình (matcher khớp đầu tiên thắng) ======
                // >>> TRACK A (order, auction)
                // <<< TRACK A
                // ------------------------------------------------------------------------------------------
                // >>> TRACK B (conversation)
                // <<< TRACK B
                // ------------------------------------------------------------------------------------------
                // >>> TRACK C (review, report, admin users)
                // <<< TRACK C
                // ------------------------------------------------------------------------------------------
                // >>> TRACK D1 (notification, feedback)
                // <<< TRACK D1
                // ------------------------------------------------------------------------------------------
                // >>> TRACK D2 (community moment, tutorial, daily need)
                // <<< TRACK D2
                // ------------------------------------------------------------------------------------------
                // >>> TRACK E (realtime, websocket)
                .requestMatchers("/ws/**").permitAll()
                // <<< TRACK E
                // ------------------------------------------------------------------------------------------

                // 2. Explicitly protect private user routes first (prevent path template collision)
                .requestMatchers("/api/v1/users/me", "/api/v1/users/me/**", "/api/v1/users/search").authenticated()

                // 3. Explicit public Category endpoints
                .requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/categories/**").permitAll()

                // 4. Explicit public User endpoints (strictly requiring UUID pattern for publicId)
                .requestMatchers(HttpMethod.GET, "/api/v1/users/banks").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/{publicId:[0-9a-fA-F]+-[0-9a-fA-F]+-[0-9a-fA-F]+-[0-9a-fA-F]+-[0-9a-fA-F]+}").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/{publicId:[0-9a-fA-F]+-[0-9a-fA-F]+-[0-9a-fA-F]+-[0-9a-fA-F]+-[0-9a-fA-F]+}/sold-products").permitAll()

                // 5. Catch-all for all other /users routes (require authentication)
                .requestMatchers("/api/v1/users/**").authenticated()

                // 6. Explicitly protect private product routes first (prevent path template collision)
                .requestMatchers("/api/v1/products/me", "/api/v1/products/me/**").authenticated()

                // 7. Explicit public Product endpoints
                .requestMatchers(HttpMethod.POST, "/api/v1/products/{productId:[0-9]+}/view").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/products/{productId:[0-9]+}").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/products").permitAll()

                // 8. Protect all other /products routes
                .requestMatchers("/api/v1/products/**").authenticated()

                // 9. Protect /favorites routes
                .requestMatchers("/api/v1/favorites", "/api/v1/favorites/**").authenticated()

                // 10. Protect /admin routes
                .requestMatchers("/api/v1/admin/**").authenticated()

                // 11. Deny-by-default for all other unmapped routes
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(Customizer.withDefaults())
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Explicit trusted development origins; matches React frontend local dev ports
        configuration.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "http://localhost:5173",
                "http://127.0.0.1:3000",
                "http://127.0.0.1:5173"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("WWW-Authenticate"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
