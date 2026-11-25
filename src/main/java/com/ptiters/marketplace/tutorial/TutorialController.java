package com.ptiters.marketplace.tutorial;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tutorials")
@RequiredArgsConstructor
public class TutorialController {

    private final TutorialService tutorialService;

    @GetMapping
    public ResponseEntity<List<PublicTutorialResponse>> listPublicTutorials(
            @RequestParam(value = "feature_key", required = false) String featureKey) {
        return ResponseEntity.ok(tutorialService.listPublicTutorials(featureKey));
    }
}
