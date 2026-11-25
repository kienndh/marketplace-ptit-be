package com.ptiters.marketplace.communitymoment;

import org.springframework.stereotype.Service;

@Service
public class CommunityMomentService {

    public CommunityMomentSubmissionResponse submitCommunityMoment(Long userId, Object imageFile, String note) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public PublicHeroResponse getPublicHeroMoments() {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public AdminCommunityMomentCursorPage listAdminCommunityMoments(int limit, String cursor, String status) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public AdminCommunityMomentItem getAdminCommunityMomentDetail(Long momentId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public CommunityMomentActionResponse approveCommunityMoment(Long momentId, Long adminId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public CommunityMomentActionResponse rejectCommunityMoment(Long momentId, Long adminId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public CommunityMomentActionResponse deleteCommunityMoment(Long momentId, Long adminId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }
}
