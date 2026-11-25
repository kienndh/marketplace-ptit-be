package com.ptiters.marketplace.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UserCapabilityRestrictionRepository extends JpaRepository<UserCapabilityRestriction, Long> {

    @Query("""
        SELECT r FROM UserCapabilityRestriction r
        WHERE r.userId = :userId
          AND r.revokedAt IS NULL
          AND r.startsAt <= :now
          AND (r.expiresAt IS NULL OR r.expiresAt > :now)
        ORDER BY r.startsAt DESC
    """)
    List<UserCapabilityRestriction> findActiveRestrictionsByUserId(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now
    );
}
