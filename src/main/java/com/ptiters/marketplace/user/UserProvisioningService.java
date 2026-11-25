package com.ptiters.marketplace.user;

import com.ptiters.marketplace.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProvisioningService {

    private final UserRepository userRepository;
    private final UserAuthIdentityRepository userAuthIdentityRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User provision(Jwt jwt) {
        if (jwt == null) {
            throw ApiException.unauthorized("Missing or invalid Authorization header.");
        }

        String providerUid = jwt.getSubject();
        if (providerUid == null || providerUid.isBlank()) {
            providerUid = jwt.getClaimAsString("uid");
        }
        if (providerUid == null || providerUid.isBlank()) {
            throw ApiException.unauthorized("Token payload missing required identity identifier.");
        }

        String rawEmail = jwt.getClaimAsString("email");
        boolean isSyntheticEmail = false;
        String email;
        if (rawEmail == null || rawEmail.isBlank()) {
            email = providerUid + "@firebase.user";
            isSyntheticEmail = true;
        } else {
            email = rawEmail.trim().toLowerCase();
        }
        if (email.length() > 150) {
            email = email.substring(0, 150);
        }

        String name = jwt.getClaimAsString("name");
        if (name == null || name.isBlank()) {
            name = email.split("@")[0];
        }
        if (name.length() > 100) {
            name = name.substring(0, 100);
        }

        String picture = jwt.getClaimAsString("picture");
        if (picture != null && picture.length() > 500) {
            picture = picture.substring(0, 500);
        }

        boolean emailVerified = Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"));

        Optional<User> existingUserOpt = isSyntheticEmail ? Optional.empty() : userRepository.findByEmail(email);
        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            Optional<UserAuthIdentity> existingIdentityOpt = userAuthIdentityRepository.findByUser_UserId(existingUser.getUserId());

            if (existingIdentityOpt.isPresent()) {
                UserAuthIdentity identity = existingIdentityOpt.get();
                if (UserAuthIdentity.PROVIDER_FIREBASE.equals(identity.getProvider()) && providerUid.equals(identity.getProviderUid())) {
                    existingUser.setLastActiveAt(LocalDateTime.now(ZoneOffset.UTC));
                    if (picture != null && !picture.equals(existingUser.getAvatarUrl())) {
                        existingUser.setAvatarUrl(picture);
                    }
                    return userRepository.save(existingUser);
                } else {
                    // Strict anti-takeover: email exists but linked to different identity
                    throw ApiException.conflict("Account already linked to a different provider identity.");
                }
            } else {
                // Legacy user without auth identity
                if (!emailVerified) {
                    throw ApiException.badRequest("Cannot link account: email is not verified.");
                }

                UserAuthIdentity identity = new UserAuthIdentity();
                identity.setUser(existingUser);
                identity.setProvider(UserAuthIdentity.PROVIDER_FIREBASE);
                identity.setProviderUid(providerUid);
                userAuthIdentityRepository.saveAndFlush(identity);

                existingUser.setLastActiveAt(LocalDateTime.now(ZoneOffset.UTC));
                if (picture != null && !picture.equals(existingUser.getAvatarUrl())) {
                    existingUser.setAvatarUrl(picture);
                }
                log.info("Linked Firebase UID {} to existing user {}", providerUid, existingUser.getUserId());
                return userRepository.save(existingUser);
            }
        }

        // New user provisioning
        User newUser = new User();
        newUser.setPublicId(UUID.randomUUID());
        newUser.setEmail(email);
        newUser.setFullName(name);
        newUser.setAvatarUrl(picture);
        newUser.setRole(UserRole.STUDENT);
        newUser.setStatus(UserStatus.ACTIVE);
        newUser.setShowPhonePublicly(false);
        newUser.setLastActiveAt(LocalDateTime.now(ZoneOffset.UTC));

        User savedUser = userRepository.saveAndFlush(newUser);

        UserAuthIdentity identity = new UserAuthIdentity();
        identity.setUser(savedUser);
        identity.setProvider(UserAuthIdentity.PROVIDER_FIREBASE);
        identity.setProviderUid(providerUid);
        userAuthIdentityRepository.saveAndFlush(identity);

        log.info("Provisioned new user {} for Firebase UID {}", savedUser.getUserId(), providerUid);
        return savedUser;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User touchExistingUser(User user, Jwt jwt) {
        user.setLastActiveAt(LocalDateTime.now(ZoneOffset.UTC));
        String picture = jwt.getClaimAsString("picture");
        if (picture != null && picture.length() <= 500 && !picture.equals(user.getAvatarUrl())) {
            user.setAvatarUrl(picture);
        }
        return userRepository.save(user);
    }
}
