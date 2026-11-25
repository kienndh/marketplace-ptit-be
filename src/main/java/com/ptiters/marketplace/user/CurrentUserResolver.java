package com.ptiters.marketplace.user;

import com.ptiters.marketplace.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserService userService;

    public Jwt getCurrentJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw ApiException.unauthorized("Missing or invalid Authorization header.");
        }
        return jwt;
    }

    public User getCurrentUser() {
        return userService.resolveUser(getCurrentJwt());
    }

    public User getCurrentActiveUser() {
        return userService.resolveActiveUser(getCurrentJwt());
    }

    public User resolveFromJwt(Jwt jwt) {
        return userService.resolveUser(jwt);
    }

    public User resolveActiveFromJwt(Jwt jwt) {
        return userService.resolveActiveUser(jwt);
    }

    public java.util.Optional<User> getOptionalCurrentActiveUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.ofNullable(userService.resolveActiveUser(jwt));
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    public User requireAdmin() {
        User user = getCurrentActiveUser();
        if (user.getRole() != UserRole.ADMIN) {
            throw ApiException.forbidden("Yêu cầu quyền quản trị viên.");
        }
        return user;
    }
}
