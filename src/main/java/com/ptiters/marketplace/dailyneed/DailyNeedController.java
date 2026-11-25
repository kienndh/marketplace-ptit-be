package com.ptiters.marketplace.dailyneed;

import com.ptiters.marketplace.user.CurrentUserResolver;
import com.ptiters.marketplace.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/daily-needs")
@RequiredArgsConstructor
public class DailyNeedController {

    private final DailyNeedService dailyNeedService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/recent")
    public ResponseEntity<DailyNeedListResponse> getRecentDailyNeeds(
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(dailyNeedService.getRecentDailyNeeds(limit));
    }

    @GetMapping("/today")
    public ResponseEntity<DailyNeedListResponse> getTodayDailyNeeds() {
        return ResponseEntity.ok(dailyNeedService.getTodayDailyNeeds());
    }

    @GetMapping("/me/today")
    public ResponseEntity<?> getMyTodayDailyNeed() {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        DailyNeedResponse response = dailyNeedService.getMyTodayDailyNeed(currentUser);
        if (response == null) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("null");
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/summary")
    public ResponseEntity<DailyNeedSummaryResponse> getMyDailyNeedSummary() {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        return ResponseEntity.ok(dailyNeedService.getMyDailyNeedSummary(currentUser));
    }

    @GetMapping("/users/{publicId}/today")
    public ResponseEntity<?> getUserTodayDailyNeed(@PathVariable UUID publicId) {
        DailyNeedPublicResponse response = dailyNeedService.getUserTodayDailyNeed(publicId);
        if (response == null) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("null");
        }
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me/today")
    public ResponseEntity<DailyNeedResponse> upsertMyTodayDailyNeed(
            @Valid @RequestBody DailyNeedCreateOrUpdateRequest request) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        return ResponseEntity.ok(dailyNeedService.upsertMyTodayDailyNeed(currentUser, request));
    }

    @DeleteMapping("/me/today")
    public ResponseEntity<Void> deleteMyTodayDailyNeed() {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        dailyNeedService.deleteMyTodayDailyNeed(currentUser);
        return ResponseEntity.noContent().build();
    }
}
