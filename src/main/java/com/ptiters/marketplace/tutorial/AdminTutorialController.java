package com.ptiters.marketplace.tutorial;

import com.ptiters.marketplace.user.CurrentUserResolver;
import com.ptiters.marketplace.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/tutorials")
@RequiredArgsConstructor
public class AdminTutorialController {

    private final TutorialService tutorialService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping("/upload-signature")
    public ResponseEntity<TutorialUploadSignatureResponse> getDirectUploadSignature() {
        currentUserResolver.requireAdmin();
        return ResponseEntity.ok(tutorialService.getUploadSignature());
    }

    @PostMapping("/create-direct")
    public ResponseEntity<AdminTutorialResponse> createDirectTutorial(
            @Valid @RequestBody AdminTutorialCreateDirectRequest request) {
        User admin = currentUserResolver.requireAdmin();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tutorialService.createDirectTutorial(admin.getUserId(), request));
    }

    @GetMapping
    public ResponseEntity<List<AdminTutorialResponse>> listAdminTutorials(
            @RequestParam(value = "feature_key", required = false) String featureKey) {
        currentUserResolver.requireAdmin();
        return ResponseEntity.ok(tutorialService.listAdminTutorials(featureKey));
    }

    @GetMapping("/{tutorialId}")
    public ResponseEntity<AdminTutorialResponse> getAdminTutorialDetail(
            @PathVariable Long tutorialId) {
        currentUserResolver.requireAdmin();
        return ResponseEntity.ok(tutorialService.getAdminTutorialDetail(tutorialId));
    }

    @PostMapping
    public ResponseEntity<AdminTutorialResponse> createTutorial(
            @RequestParam(value = "video", required = false) MultipartFile video,
            @RequestParam(value = "feature_key", required = false) String featureKey,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "action_key", required = false) String actionKey,
            @RequestParam(value = "is_published", defaultValue = "false") boolean isPublished) {
        User admin = currentUserResolver.requireAdmin();
        AdminTutorialResponse response = tutorialService.createTutorial(
                admin.getUserId(), featureKey, title, description, actionKey, isPublished, video
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/reorder")
    public ResponseEntity<List<AdminTutorialResponse>> reorderTutorials(
            @Valid @RequestBody AdminTutorialReorderRequest request) {
        currentUserResolver.requireAdmin();
        return ResponseEntity.ok(tutorialService.reorderTutorials(request));
    }

    @PatchMapping("/{tutorialId}")
    public ResponseEntity<AdminTutorialResponse> updateTutorialMetadata(
            @PathVariable Long tutorialId,
            @Valid @RequestBody AdminTutorialUpdateRequest request) {
        currentUserResolver.requireAdmin();
        return ResponseEntity.ok(tutorialService.updateTutorialMetadata(tutorialId, request));
    }

    @PostMapping("/{tutorialId}/video")
    public ResponseEntity<AdminTutorialResponse> replaceTutorialVideo(
            @PathVariable Long tutorialId,
            @RequestParam(value = "video", required = false) MultipartFile video) {
        currentUserResolver.requireAdmin();
        return ResponseEntity.ok(tutorialService.replaceTutorialVideo(tutorialId, video));
    }

    @PostMapping("/{tutorialId}/confirm-direct-video")
    public ResponseEntity<AdminTutorialResponse> confirmDirectTutorialVideo(
            @PathVariable Long tutorialId,
            @Valid @RequestBody AdminTutorialConfirmDirectVideo request) {
        currentUserResolver.requireAdmin();
        return ResponseEntity.ok(tutorialService.confirmDirectTutorialVideo(tutorialId, request));
    }

    @DeleteMapping("/{tutorialId}")
    public ResponseEntity<Map<String, String>> deleteTutorial(
            @PathVariable Long tutorialId) {
        currentUserResolver.requireAdmin();
        return ResponseEntity.ok(tutorialService.deleteTutorial(tutorialId));
    }
}
