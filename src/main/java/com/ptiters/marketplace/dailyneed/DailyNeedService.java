package com.ptiters.marketplace.dailyneed;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class DailyNeedService {

    public List<DailyNeedPublicResponse> getRecentDailyNeeds(int limit) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public List<DailyNeedPublicResponse> getTodayDailyNeeds(int limit) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public DailyNeedResponse getMyTodayDailyNeed(Long userId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public DailyNeedSummaryResponse getMyDailyNeedSummary(Long userId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public DailyNeedPublicResponse getUserTodayDailyNeed(UUID publicId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public DailyNeedResponse upsertMyTodayDailyNeed(Long userId, DailyNeedCreateOrUpdateRequest request) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public void deleteMyTodayDailyNeed(Long userId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }
}
