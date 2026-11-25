package com.ptiters.marketplace.notification;

import com.ptiters.marketplace.user.CurrentUserResolver;
import com.ptiters.marketplace.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping("/push/subscribe")
    public ResponseEntity<PushSubscriptionResponse> subscribePushToken(
            @Valid @RequestBody PushSubscribeRequest request
    ) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        return ResponseEntity.ok(notificationService.subscribePushToken(currentUser.getUserId(), request));
    }

    @PostMapping("/push/unsubscribe")
    public ResponseEntity<PushSubscriptionResponse> unsubscribePushToken(
            @Valid @RequestBody PushUnsubscribeRequest request
    ) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        return ResponseEntity.ok(notificationService.unsubscribePushToken(currentUser.getUserId(), request));
    }

    @GetMapping
    public ResponseEntity<NotificationListResponse> listNotifications(
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "unread_only", defaultValue = "false") boolean unreadOnly
    ) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        return ResponseEntity.ok(
                notificationService.listNotifications(currentUser.getUserId(), limit, cursor, unreadOnly)
        );
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount() {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        return ResponseEntity.ok(notificationService.getUnreadCount(currentUser.getUserId()));
    }

    // Route tĩnh /read-all đăng ký trước /{notificationId}/read
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        notificationService.markAllRead(currentUser.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long notificationId) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        notificationService.markRead(currentUser.getUserId(), notificationId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long notificationId) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        notificationService.deleteNotification(currentUser.getUserId(), notificationId);
        return ResponseEntity.noContent().build();
    }
}
