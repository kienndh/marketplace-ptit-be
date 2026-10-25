package com.ptiters.marketplace.configuration;

import com.ptiters.marketplace.exception.ApiException;
import com.ptiters.marketplace.security.AuthService;
import com.ptiters.marketplace.security.FirebaseAuthFilter;
import com.ptiters.marketplace.security.FirebaseTokenVerifier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            FirebaseTokenVerifier tokenVerifier,
            AuthService authService,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver)
            throws Exception {

        FirebaseAuthFilter firebaseFilter =
                new FirebaseAuthFilter(tokenVerifier, authService, exceptionResolver);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/banks").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/{userId:\\d+}").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> exceptionResolver.resolveException(
                                req, res, null,
                                ApiException.unauthorized("Missing or invalid Authorization header.")))
                        .accessDeniedHandler((req, res, e) -> exceptionResolver.resolveException(
                                req, res, null, ApiException.forbidden("Forbidden."))))
                .addFilterBefore(firebaseFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Tắt tài khoản mặc định (user + mật khẩu sinh ngẫu nhiên) mà Spring Boot tự tạo
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }
}