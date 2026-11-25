package com.ptiters.marketplace.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ptiters.marketplace.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationQueryRepository notificationQueryRepository;
    private final DeviceTokenStore deviceTokenStore;
    private final ObjectMapper objectMapper;

    record CursorPayload(
            @JsonProperty("created_at") String createdAt,
            @JsonProperty("notification_id") Long notificationId
    ) {}

    public record DecodedCursor(LocalDateTime createdAt, Long notificationId) {}

    public String encodeCursor(LocalDateTime createdAt, Long notificationId) {
        if (createdAt == null || notificationId == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(new CursorPayload(createdAt.toString(), notificationId));
            return Base64.getUrlEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode notification cursor", e);
        }
    }

    public DecodedCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor.trim());
            String json = new String(decoded, StandardCharsets.UTF_8);
            CursorPayload payload = objectMapper.readValue(json, CursorPayload.class);
            if (payload == null || payload.createdAt() == null || payload.notificationId() == null) {
                return null;
            }
            LocalDateTime parsedTime = parseIsoLocalDateTime(payload.createdAt());
            return new DecodedCursor(parsedTime, payload.notificationId());
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime parseIsoLocalDateTime(String raw) {
        try {
            return LocalDateTime.parse(raw);
        } catch (Exception ignored) {
            return OffsetDateTime.parse(raw).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        }
    }

    @Transactional
    public PushSubscriptionResponse subscribePushToken(Long userId, PushSubscribeRequest request) {
        String token = request.fcmToken() == null ? "" : request.fcmToken().trim();
        if (token.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FCM token không được để trống.");
        }
        deviceTokenStore.upsertToken(userId, token, request.deviceInfo());
        return new PushSubscriptionResponse("subscribed");
    }

    @Transactional
    public PushSubscriptionResponse unsubscribePushToken(Long userId, PushUnsubscribeRequest request) {
        String token = request.fcmToken() == null ? "" : request.fcmToken().trim();
        if (token.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FCM token không được để trống.");
        }
        deviceTokenStore.deleteToken(userId, token);
        return new PushSubscriptionResponse("unsubscribed");
    }

    public NotificationListResponse listNotifications(Long userId, int limit, String cursor, boolean unreadOnly) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        DecodedCursor decoded = decodeCursor(cursor);
        LocalDateTime cursorTime = decoded != null ? decoded.createdAt() : null;
        Long cursorId = decoded != null ? decoded.notificationId() : null;

        List<Notification> fetched = notificationQueryRepository.listForUser(
                userId,
                safeLimit,
                cursorTime,
                cursorId,
                unreadOnly
        );

        boolean hasMore = fetched.size() > safeLimit;
        List<Notification> pageItems = hasMore ? fetched.subList(0, safeLimit) : fetched;

        String nextCursor = null;
        if (hasMore && !pageItems.isEmpty()) {
            Notification last = pageItems.get(pageItems.size() - 1);
            nextCursor = encodeCursor(last.getCreatedAt(), last.getNotificationId());
        }

        List<NotificationResponse> items = pageItems.stream()
                .map(NotificationResponse::from)
                .toList();

        return new NotificationListResponse(items, nextCursor, hasMore);
    }

    public UnreadCountResponse getUnreadCount(Long userId) {
        return new UnreadCountResponse(notificationQueryRepository.countUnread(userId));
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationQueryRepository.markAllRead(userId);
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        boolean found = notificationQueryRepository.markRead(notificationId, userId);
        if (!found) {
            throw ApiException.notFound("Không tìm thấy thông báo.");
        }
    }

    @Transactional
    public void deleteNotification(Long userId, Long notificationId) {
        boolean deleted = notificationQueryRepository.deleteUserNotification(notificationId, userId);
        if (!deleted) {
            throw ApiException.notFound("Không tìm thấy thông báo.");
        }
    }
}
