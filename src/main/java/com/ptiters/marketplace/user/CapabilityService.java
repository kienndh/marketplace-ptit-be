package com.ptiters.marketplace.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CapabilityService {

    private final UserCapabilityRestrictionRepository restrictionRepository;

    @Transactional(readOnly = true)
    public UserAccessStatusResponse getUserAccessStatus(User user) {
        if (user.getStatus() == UserStatus.BANNED || user.getStatus() == UserStatus.INACTIVE) {
            return new UserAccessStatusResponse(
                    user.getUserId(),
                    user.getStatus().name(),
                    user.getRole().name(),
                    false,
                    false,
                    Collections.emptyList()
            );
        }

        if (user.getRole() == UserRole.ADMIN) {
            return new UserAccessStatusResponse(
                    user.getUserId(),
                    user.getStatus().name(),
                    user.getRole().name(),
                    true,
                    true,
                    Collections.emptyList()
            );
        }

        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        List<UserCapabilityRestriction> activeRestrictions =
                restrictionRepository.findActiveRestrictionsByUserId(user.getUserId(), nowUtc);

        boolean canBuy = activeRestrictions.stream().noneMatch(r -> "BUY".equalsIgnoreCase(r.getCapability()));
        boolean canSell = activeRestrictions.stream().noneMatch(r -> "SELL".equalsIgnoreCase(r.getCapability()));

        List<UserCapabilityRestrictionResponse> restrictionResponses = activeRestrictions.stream()
                .map(r -> new UserCapabilityRestrictionResponse(r.getCapability(), r.getExpiresAt()))
                .toList();

        return new UserAccessStatusResponse(
                user.getUserId(),
                user.getStatus().name(),
                user.getRole().name(),
                canBuy,
                canSell,
                restrictionResponses
        );
    }
}
