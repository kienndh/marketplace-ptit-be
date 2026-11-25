package com.ptiters.marketplace.notification;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/push/subscribe")
    public ResponseEntity<PushSubscriptionResponse> subscribePushToken(
            @Valid @RequestBody PushSubscribeRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(notificationService.subscribePushToken(null, request));
    }

    @PostMapping("/push/unsubscribe")
    public ResponseEntity<PushSubscriptionResponse> unsubscribePushToken(
            @Valid @RequestBody PushUnsubscribeRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(notificationService.unsubscribePushToken(null, request));
    }

    @GetMapping
    public ResponseEntity<NotificationListResponse> listNotifications(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(notificationService.listNotifications(null, limit, offset));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(notificationService.getUnreadCount(null));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(
            @AuthenticationPrincipal UserDetails userDetails) {
        notificationService.markAllRead(null);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal UserDetails userDetails) {
        notificationService.markRead(null, notificationId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal UserDetails userDetails) {
        notificationService.deleteNotification(null, notificationId);
        return ResponseEntity.noContent().build();
    }
}
