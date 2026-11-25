package com.ptiters.marketplace.notification;

import java.util.List;
import java.util.Optional;

public interface UserDeviceTokenRepository {
    List<UserDeviceToken> findByUserId(Long userId);
    Optional<UserDeviceToken> findByToken(String token);
    UserDeviceToken save(UserDeviceToken deviceToken);
    void deleteByToken(String token);
    void deleteByUserId(Long userId);
}
