package com.ptiters.marketplace.user;

import com.ptiters.marketplace.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserAuthIdentityRepository userAuthIdentityRepository;
    private final UserProvisioningService userProvisioningService;
    private final UserProfileQueryRepository userProfileQueryRepository;

    public Optional<User> findByIdentity(String provider, String providerUid) {
        return userAuthIdentityRepository.findByProviderAndProviderUid(provider, providerUid)
                .map(UserAuthIdentity::getUser);
    }

    public User resolveUser(Jwt jwt) {
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

        // 1. Fast path: check existing identity
        Optional<User> existingUserOpt = findByIdentity(UserAuthIdentity.PROVIDER_FIREBASE, providerUid);
        if (existingUserOpt.isPresent()) {
            try {
                return userProvisioningService.touchExistingUser(existingUserOpt.get(), jwt);
            } catch (Exception e) {
                log.warn("Failed to update last_active_at/avatar for UID {}: {}", providerUid, e.getMessage());
                return existingUserOpt.get();
            }
        }

        // 2. First-login provisioning path with race condition mitigation
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return userProvisioningService.provision(jwt);
            } catch (DataIntegrityViolationException e) {
                log.warn("DataIntegrityViolationException during user provisioning for UID {}: attempt {}", providerUid, attempt);
                // Concurrency retry: check if a concurrent thread completed provisioning
                Optional<User> retryUser = findByIdentity(UserAuthIdentity.PROVIDER_FIREBASE, providerUid);
                if (retryUser.isPresent()) {
                    return retryUser.get();
                }

                if (attempt == 2) {
                    String email = jwt.getClaimAsString("email");
                    if (email != null && userRepository.findByEmail(email).isPresent()) {
                        throw ApiException.conflict("Account already linked to a different provider identity.");
                    }
                    throw ApiException.conflict("User account provisioning conflict. Please retry.");
                }
            }
        }

        throw ApiException.conflict("User account provisioning conflict. Please retry.");
    }

    public User requireActiveUser(User user) {
        if (user == null) {
            throw ApiException.unauthorized("Missing or invalid Authorization header.");
        }

        if (user.getStatus() == UserStatus.BANNED) {
            throw ApiException.forbidden("User account is banned.");
        }

        if (user.getStatus() == UserStatus.INACTIVE) {
            throw ApiException.forbidden("User account is inactive.");
        }

        return user;
    }

    public User resolveActiveUser(Jwt jwt) {
        User user = resolveUser(jwt);
        return requireActiveUser(user);
    }

    @Transactional
    public User updateProfile(User user, UserUpdate payload) {
        if (payload == null) {
            return user;
        }

        if (payload.getFullName() != null) {
            String trimmedName = payload.getFullName().trim();
            if (trimmedName.isEmpty() || trimmedName.length() > 100) {
                throw ApiException.badRequest("Họ tên phải từ 1 đến 100 ký tự.");
            }
            user.setFullName(trimmedName);
        }

        if (payload.getPhone() != null) {
            String normalizedPhone = VietnamPhoneUtils.normalize(payload.getPhone());
            user.setPhone(normalizedPhone);
        }

        if (payload.getShowPhonePublicly() != null) {
            user.setShowPhonePublicly(payload.getShowPhonePublicly());
        }

        if (payload.getSocialLinks() != null) {
            SocialLinkValidator.validateLinks(payload.getSocialLinks());
            user.setSocialLinks(payload.getSocialLinks());
        }

        if (payload.getBio() != null) {
            String bio = payload.getBio().trim();
            if (bio.length() > 500) {
                throw ApiException.badRequest("Bio tối đa 500 ký tự.");
            }
            user.setBio(bio.isEmpty() ? null : bio);
        }

        if (payload.getDeliveryArea() != null) {
            String deliveryArea = payload.getDeliveryArea().trim();
            if (deliveryArea.length() > 255) {
                throw ApiException.badRequest("Khu vực giao hàng tối đa 255 ký tự.");
            }
            user.setDeliveryArea(deliveryArea.isEmpty() ? null : deliveryArea);
        }

        if (payload.getDefaultMeetupArea() != null) {
            String meetupArea = payload.getDefaultMeetupArea().trim();
            if (meetupArea.length() > 255) {
                throw ApiException.badRequest("Khu vực gặp gỡ tối đa 255 ký tự.");
            }
            user.setDefaultMeetupArea(meetupArea.isEmpty() ? null : meetupArea);
        }

        return userRepository.save(user);
    }

    public PublicUserProfileResponse getPublicProfile(UUID publicId) {
        if (publicId == null) {
            throw ApiException.notFound("Không tìm thấy người dùng.");
        }

        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng."));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.notFound("Không tìm thấy người dùng.");
        }

        int activeListings = userProfileQueryRepository.countActiveListings(user.getUserId());
        int completedSold = userProfileQueryRepository.countCompletedSold(user.getUserId());
        UserProfileQueryRepository.ReviewStats reviewStats = userProfileQueryRepository.getReviewStats(user.getUserId());

        String publicPhone = (user.isShowPhonePublicly() && user.getPhone() != null) ? user.getPhone() : null;

        return new PublicUserProfileResponse(
                user.getPublicId(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getDeliveryArea(),
                publicPhone,
                user.getSocialLinks(),
                user.getCreatedAt(),
                activeListings,
                completedSold,
                reviewStats.averageRating(),
                reviewStats.reviewCount()
        );
    }

    public SoldProductListResponse getSoldProducts(UUID publicId) {
        if (publicId == null) {
            throw ApiException.notFound("Không tìm thấy người dùng.");
        }

        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng."));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.notFound("Không tìm thấy người dùng.");
        }

        return userProfileQueryRepository.getSoldProducts(user.getUserId());
    }

    public BankAccountResponse getBankAccount(User user) {
        if (user == null || user.getBankBin() == null || user.getBankAccountNumber() == null || user.getBankAccountName() == null) {
            return null;
        }

        Optional<SupportedBank> bankOpt = SupportedBanksRegistry.getBankByBin(user.getBankBin());
        String code = bankOpt.map(SupportedBank::getCode).orElse(user.getBankBin());
        String name = bankOpt.map(SupportedBank::getName).orElse("");
        String shortName = bankOpt.map(SupportedBank::getShortName).orElse(code);

        return new BankAccountResponse(
                user.getBankBin(),
                code,
                name,
                shortName,
                user.getBankAccountNumber(),
                user.getBankAccountName()
        );
    }

    @Transactional
    public BankAccountResponse updateBankAccount(User user, BankAccountUpdate payload) {
        if (user == null || payload == null) {
            throw ApiException.badRequest("Dữ liệu tài khoản ngân hàng không hợp lệ.");
        }

        String rawBin = payload.getBankBin();
        if (rawBin == null || rawBin.trim().isEmpty()) {
            throw ApiException.badRequest("Mã ngân hàng không được để trống.");
        }
        String resolvedBin = SupportedBanksRegistry.resolveBankBin(rawBin)
                .orElseThrow(() -> ApiException.badRequest("Mã ngân hàng (BIN/Code) không hợp lệ hoặc không được hỗ trợ."));

        String rawAccountNumber = payload.getAccountNumber();
        if (rawAccountNumber == null || rawAccountNumber.trim().isEmpty()) {
            throw ApiException.badRequest("Số tài khoản không được để trống.");
        }
        String cleanAccountNumber = rawAccountNumber.trim();
        if (!cleanAccountNumber.matches("^\\d+$")) {
            throw ApiException.badRequest("Số tài khoản chỉ được chứa chữ số (0-9), không chứa chữ cái, khoảng trắng hoặc ký tự đặc biệt.");
        }
        if (cleanAccountNumber.length() < 4 || cleanAccountNumber.length() > 30) {
            throw ApiException.badRequest("Độ dài số tài khoản không hợp lệ (4 - 30 chữ số).");
        }

        String rawAccountName = payload.getAccountName();
        if (rawAccountName == null || rawAccountName.trim().isEmpty()) {
            throw ApiException.badRequest("Tên chủ tài khoản không được để trống.");
        }
        String cleanAccountName = rawAccountName.trim().replaceAll("\\s+", " ");
        if (cleanAccountName.length() < 2 || cleanAccountName.length() > 100) {
            throw ApiException.badRequest("Tên chủ tài khoản phải từ 2 đến 100 ký tự.");
        }

        boolean hasLetter = false;
        for (int i = 0; i < cleanAccountName.length(); i++) {
            char c = cleanAccountName.charAt(i);
            int type = Character.getType(c);
            if (type == Character.CONTROL || type == Character.FORMAT || type == Character.PRIVATE_USE || type == Character.SURROGATE || type == Character.UNASSIGNED) {
                throw ApiException.badRequest("Tên chủ tài khoản không được chứa ký tự điều khiển.");
            }
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (c != ' ' && c != '.' && c != '\'' && c != '-') {
                throw ApiException.badRequest("Tên chủ tài khoản chỉ được chứa chữ cái, khoảng trắng và dấu tên hợp lệ.");
            }
        }
        if (!hasLetter) {
            throw ApiException.badRequest("Tên chủ tài khoản phải chứa chữ cái.");
        }

        user.setBankBin(resolvedBin);
        user.setBankAccountNumber(cleanAccountNumber);
        user.setBankAccountName(cleanAccountName);

        User savedUser = userRepository.save(user);
        return getBankAccount(savedUser);
    }

    @Transactional
    public void deleteBankAccount(User user) {
        if (user == null) {
            return;
        }
        user.setBankBin(null);
        user.setBankAccountNumber(null);
        user.setBankAccountName(null);
        userRepository.save(user);
    }

    public List<UserSearchResponse> searchUsers(String query, int limit) {
        throw new UnsupportedOperationException("TODO: Implement user search");
    }
}
