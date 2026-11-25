package com.ptiters.marketplace.dailyneed;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/daily-needs")
public class DailyNeedController {

    private final DailyNeedService dailyNeedService;

    public DailyNeedController(DailyNeedService dailyNeedService) {
        this.dailyNeedService = dailyNeedService;
    }

    @GetMapping("/recent")
    public ResponseEntity<List<DailyNeedPublicResponse>> getRecentDailyNeeds(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(dailyNeedService.getRecentDailyNeeds(limit));
    }

    @GetMapping("/today")
    public ResponseEntity<List<DailyNeedPublicResponse>> getTodayDailyNeeds(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(dailyNeedService.getTodayDailyNeeds(limit));
    }

    @GetMapping("/me/today")
    public ResponseEntity<DailyNeedResponse> getMyTodayDailyNeed(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(dailyNeedService.getMyTodayDailyNeed(null));
    }

    @GetMapping("/me/summary")
    public ResponseEntity<DailyNeedSummaryResponse> getMyDailyNeedSummary(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(dailyNeedService.getMyDailyNeedSummary(null));
    }

    @GetMapping("/users/{publicId}/today")
    public ResponseEntity<DailyNeedPublicResponse> getUserTodayDailyNeed(
            @PathVariable UUID publicId) {
        return ResponseEntity.ok(dailyNeedService.getUserTodayDailyNeed(publicId));
    }

    @PutMapping("/me/today")
    public ResponseEntity<DailyNeedResponse> upsertMyTodayDailyNeed(
            @Valid @RequestBody DailyNeedCreateOrUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(dailyNeedService.upsertMyTodayDailyNeed(null, request));
    }

    @DeleteMapping("/me/today")
    public ResponseEntity<Void> deleteMyTodayDailyNeed(
            @AuthenticationPrincipal UserDetails userDetails) {
        dailyNeedService.deleteMyTodayDailyNeed(null);
        return ResponseEntity.noContent().build();
    }
}
