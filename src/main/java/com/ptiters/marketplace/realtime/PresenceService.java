package com.ptiters.marketplace.realtime;

import java.util.Collection;
import java.util.Set;

/**
 * Presence người dùng (Redis ở Track E). Tương ứng {@code app/core/realtime/presence.py}.
 * Khi Redis không sẵn sàng: {@code available=false} và tập online rỗng — KHÔNG ném lỗi.
 */
public interface PresenceService {

    /** Python: {@code (presence_available, online_user_ids)}. */
    record PresenceSnapshot(boolean available, Set<Long> onlineUserIds) {
        public static PresenceSnapshot unavailable() {
            return new PresenceSnapshot(false, Set.of());
        }
    }

    /** Python: {@code (presence_available, is_online)}. */
    record UserPresence(boolean available, boolean online) {
    }

    /** Python: {@code record_user_presence(user_id)} — làm mới heartbeat; false nếu Redis không sẵn sàng. */
    boolean recordUserPresence(Long userId);

    /** Python: {@code get_users_presence(user_ids)}. */
    PresenceSnapshot getUsersPresence(Collection<Long> userIds);

    /** Python: {@code is_user_online(user_id)}. */
    default UserPresence isUserOnline(Long userId) {
        PresenceSnapshot snapshot = getUsersPresence(Set.of(userId));
        return new UserPresence(snapshot.available(), snapshot.onlineUserIds().contains(userId));
    }
}
