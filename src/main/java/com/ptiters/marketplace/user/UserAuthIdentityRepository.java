package com.ptiters.marketplace.user;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAuthIdentityRepository extends JpaRepository<UserAuthIdentity, Long> {

    // Lấy luôn user trong cùng một truy vấn, tránh truy vấn thừa
    @EntityGraph(attributePaths = "user")
    Optional<UserAuthIdentity> findByProviderAndProviderUid(String provider, String providerUid);
}