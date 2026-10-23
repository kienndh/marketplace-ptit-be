package com.ptiters.marketplace.security;

import com.google.firebase.auth.FirebaseToken;
import com.ptiters.marketplace.exception.ApiException;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserAuthIdentity;
import com.ptiters.marketplace.user.UserAuthIdentityRepository;
import com.ptiters.marketplace.user.UserRepository;
import com.ptiters.marketplace.user.UserRole;
import com.ptiters.marketplace.user.UserStatus;
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@AllArgsConstructor
public class AuthService {

    private UserRepository userRepository;
    private UserAuthIdentityRepository identityRepository;
    private EntityManager entityManager;

    @Transactional
    public User authenticate(FirebaseToken token) {
        User user = identityRepository
                .findByProviderAndProviderUid(UserAuthIdentity.PROVIDER_FIREBASE, token.getUid())
                .map(UserAuthIdentity::getUser)
                .orElseGet(() -> provision(token));

        if (user.getStatus() == UserStatus.BANNED) {
            throw ApiException.forbidden("User account is banned.");
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw ApiException.forbidden("User account is inactive.");
        }

        // avatar_url được đồng bộ từ tài khoản Google/Firebase
        String picture = token.getPicture();
        if (picture != null && picture.length() <= 500 && !picture.equals(user.getAvatarUrl())) {
            user.setAvatarUrl(picture);
        }
        return user;
    }

    // Đăng nhập lần đầu: tạo users + user_auth_identities trong cùng một transaction
    private User provision(FirebaseToken token) {
        String email = token.getEmail();
        if (email == null || email.isBlank()) {
            throw ApiException.unauthorized("Invalid Firebase token.");
        }

        User user = new User();
        user.setEmail(email);
        user.setFullName(resolveFullName(token, email));
        user.setRole(UserRole.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
        user.setLastActiveAt(LocalDateTime.now(ZoneOffset.UTC));
        userRepository.saveAndFlush(user);

        UserAuthIdentity identity = new UserAuthIdentity();
        identity.setUser(user);
        identity.setProvider(UserAuthIdentity.PROVIDER_FIREBASE);
        identity.setProviderUid(token.getUid());
        identityRepository.saveAndFlush(identity);

        // created_at, updated_at do database tự điền, nên nạp lại để có giá trị
        entityManager.refresh(user);
        return user;
    }

    // Token Google có "name"; token Email/Password thì không, lấy phần trước dấu @ của email
    private String resolveFullName(FirebaseToken token, String email) {
        String name = token.getName();
        if (name == null || name.isBlank()) {
            int at = email.indexOf('@');
            name = at > 0 ? email.substring(0, at) : email;
        }
        name = name.trim();
        return name.length() > 100 ? name.substring(0, 100) : name;
    }
}