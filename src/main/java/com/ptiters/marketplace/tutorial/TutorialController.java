package com.ptiters.marketplace.tutorial;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tutorials")
public class TutorialController {

    private final TutorialService tutorialService;

    public TutorialController(TutorialService tutorialService) {
        this.tutorialService = tutorialService;
    }

    @GetMapping
    public ResponseEntity<List<PublicTutorialResponse>> listPublicTutorials(
            @RequestParam(required = false) String featureKey) {
        return ResponseEntity.ok(tutorialService.listPublicTutorials(featureKey));
    }
}
