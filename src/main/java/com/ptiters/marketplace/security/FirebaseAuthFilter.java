package com.ptiters.marketplace.security;

import com.google.firebase.auth.FirebaseToken;
import com.ptiters.marketplace.exception.ApiException;
import com.ptiters.marketplace.user.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.List;

@AllArgsConstructor
public class FirebaseAuthFilter extends OncePerRequestFilter {

    private FirebaseTokenVerifier tokenVerifier;
    private AuthService authService;
    private HandlerExceptionResolver exceptionResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String bearerPrefix = "Bearer ";
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        // Không có header: cho đi tiếp, endpoint nào cần đăng nhập sẽ tự trả 401
        if (header == null) {
            chain.doFilter(request, response);
            return;
        }

        if (!header.startsWith(bearerPrefix) || header.length() == bearerPrefix.length()) {
            exceptionResolver.resolveException(request, response, null,
                    ApiException.unauthorized("Missing or invalid Authorization header."));
            return;
        }

        try {
            FirebaseToken token = tokenVerifier.verify(header.substring(bearerPrefix.length()).trim());
            User user = authService.authenticate(token);

            AuthenticatedUser principal =
                    new AuthenticatedUser(user.getUserId(), user.getEmail(), user.getRole());
            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            // Giao cho ErrorHandler để lỗi ra cùng định dạng {"detail": ...}
            exceptionResolver.resolveException(request, response, null, e);
            return;
        }

        chain.doFilter(request, response);
    }
}