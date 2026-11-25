package com.ptiters.marketplace.realtime;

import java.util.Collection;
import java.util.Set;

/** Bản mặc định: giống Python khi Redis không có — không khả dụng, không ai online. */
public class NoOpPresenceService implements PresenceService {

    @Override
    public boolean recordUserPresence(Long userId) {
        return false;
    }

    @Override
    public PresenceSnapshot getUsersPresence(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new PresenceSnapshot(true, Set.of());
        }
        return PresenceSnapshot.unavailable();
    }
}
