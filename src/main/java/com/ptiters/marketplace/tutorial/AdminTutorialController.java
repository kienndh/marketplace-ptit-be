package com.ptiters.marketplace.tutorial;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/tutorials")
public class AdminTutorialController {

    private final TutorialService tutorialService;

    public AdminTutorialController(TutorialService tutorialService) {
        this.tutorialService = tutorialService;
    }

    @PostMapping("/upload-signature")
    public ResponseEntity<TutorialUploadSignatureResponse> getDirectUploadSignature(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(tutorialService.getUploadSignature(null));
    }

    @PostMapping("/create-direct")
    public ResponseEntity<AdminTutorialResponse> createDirectTutorial(
            @Valid @RequestBody AdminTutorialCreateDirectRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tutorialService.createDirectTutorial(null, request));
    }

    @GetMapping
    public ResponseEntity<List<AdminTutorialResponse>> listAdminTutorials() {
        return ResponseEntity.ok(tutorialService.listAdminTutorials());
    }

    @GetMapping("/{tutorialId}")
    public ResponseEntity<AdminTutorialResponse> getAdminTutorialDetail(
            @PathVariable Long tutorialId) {
        return ResponseEntity.ok(tutorialService.getAdminTutorialDetail(tutorialId));
    }

    @PostMapping
    public ResponseEntity<AdminTutorialResponse> createTutorial(
            @RequestParam("file") Object file,
            @RequestParam("title") String title,
            @RequestParam("feature_key") String featureKey,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tutorialService.createTutorial(null, file, title, featureKey));
    }

    @PatchMapping("/reorder")
    public ResponseEntity<Void> reorderTutorials(
            @Valid @RequestBody AdminTutorialReorderRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        tutorialService.reorderTutorials(null, request);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{tutorialId}")
    public ResponseEntity<AdminTutorialResponse> updateTutorialMetadata(
            @PathVariable Long tutorialId,
            @Valid @RequestBody AdminTutorialUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(tutorialService.updateTutorialMetadata(tutorialId, null, request));
    }

    @PostMapping("/{tutorialId}/video")
    public ResponseEntity<AdminTutorialResponse> replaceTutorialVideo(
            @PathVariable Long tutorialId,
            @RequestParam("file") Object file,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(tutorialService.replaceTutorialVideo(tutorialId, null, file));
    }

    @PostMapping("/{tutorialId}/confirm-direct-video")
    public ResponseEntity<AdminTutorialResponse> confirmDirectTutorialVideo(
            @PathVariable Long tutorialId,
            @Valid @RequestBody AdminTutorialConfirmDirectVideo request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(tutorialService.confirmDirectTutorialVideo(tutorialId, null, request));
    }

    @DeleteMapping("/{tutorialId}")
    public ResponseEntity<Void> deleteTutorial(
            @PathVariable Long tutorialId,
            @AuthenticationPrincipal UserDetails userDetails) {
        tutorialService.deleteTutorial(tutorialId, null);
        return ResponseEntity.noContent().build();
    }
}
