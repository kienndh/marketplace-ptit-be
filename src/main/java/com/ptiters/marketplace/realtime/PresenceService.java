package com.ptiters.marketplace.realtime;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class PresenceService {

    public boolean recordPresence(Long userId) {
        throw new UnsupportedOperationException("TODO: Implement user presence record in Redis");
    }

    public boolean isUserOnline(Long userId) {
        throw new UnsupportedOperationException("TODO: Implement presence check in Redis");
    }

    public Set<Long> getOnlineUsers(List<Long> userIds) {
        throw new UnsupportedOperationException("TODO: Implement batch presence check in Redis");
    }
}
