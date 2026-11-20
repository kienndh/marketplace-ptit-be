package com.ptiters.marketplace.user;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPublicId(UUID publicId);

    Optional<User> findFirstByStatus(UserStatus status);

    @Query("SELECT u FROM User u WHERE u.socialLinks IS NOT NULL")
    List<User> findUsersWithSocialLinks(Pageable pageable);
}
