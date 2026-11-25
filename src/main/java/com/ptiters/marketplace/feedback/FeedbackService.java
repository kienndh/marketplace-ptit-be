package com.ptiters.marketplace.feedback;

import org.springframework.stereotype.Service;

@Service
public class FeedbackService {

    public FeedbackCreateResponse submitFeedback(Long userId, FeedbackCreateRequest request) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public PublicFeedbackCursorPage listPublicFeedbacks(int limit, String cursor) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public MyFeedbackCursorPage listMyFeedbacks(Long userId, int limit, String cursor) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public void deleteMyFeedback(Long userId, Long feedbackId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public SupporterLeaderboardResponse getSupporterLeaderboard() {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public SupportResponse getSupportInfo() {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public SupportClaimUserItem submitSupportClaim(Long userId, SupportClaimCreate request) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public SupportClaimUserListPage listMySupportClaims(Long userId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public AdminFeedbackListPage listAdminFeedbacks(int page, int size) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public AdminFeedbackDetail getAdminFeedbackDetail(Long feedbackId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public void deleteAdminFeedback(Long feedbackId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public Object streamFeedbackEvidence(Long feedbackId, Long attachmentId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public Object searchSupporters(String query) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public AdminSupportContributionItem createSupportContribution(AdminSupportContributionCreate request) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public AdminSupportContributionCursorPage listSupportContributions(int limit, String cursor) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public void updateSupportContributionVisibility(Long contributionId, AdminSupportContributionVisibilityUpdate request) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public void deleteSupportContribution(Long contributionId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public AdminSupportClaimCursorPage listSupportClaims(int limit, String cursor) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public void verifySupportClaim(Long claimId, AdminSupportClaimVerify request) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public void rejectSupportClaim(Long claimId, AdminSupportClaimReject request) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }
}
