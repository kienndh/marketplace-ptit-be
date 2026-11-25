package com.ptiters.marketplace.tutorial;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TutorialService {

    public List<PublicTutorialResponse> listPublicTutorials(String featureKey) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public TutorialUploadSignatureResponse getUploadSignature(Long adminId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public AdminTutorialResponse createDirectTutorial(Long adminId, AdminTutorialCreateDirectRequest request) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public List<AdminTutorialResponse> listAdminTutorials() {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public AdminTutorialResponse getAdminTutorialDetail(Long tutorialId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public AdminTutorialResponse createTutorial(Long adminId, Object file, String title, String featureKey) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public void reorderTutorials(Long adminId, AdminTutorialReorderRequest request) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public AdminTutorialResponse updateTutorialMetadata(Long tutorialId, Long adminId, AdminTutorialUpdateRequest request) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public AdminTutorialResponse replaceTutorialVideo(Long tutorialId, Long adminId, Object file) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public AdminTutorialResponse confirmDirectTutorialVideo(Long tutorialId, Long adminId, AdminTutorialConfirmDirectVideo request) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public void deleteTutorial(Long tutorialId, Long adminId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }
}
