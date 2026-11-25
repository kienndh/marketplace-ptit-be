package com.ptiters.marketplace.integration;

import com.ptiters.marketplace.notification.PushNotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Triển khai {@link PushNotificationSender} dùng Firebase Cloud Messaging (FCM) và {@link JdbcTemplate}.
 *
 * <p>Chỉ kích hoạt khi có {@code FIREBASE_CREDENTIALS_JSON} hoặc {@code FIREBASE_CREDENTIALS_PATH};
 * khi thiếu cấu hình, {@code SharedInfrastructureDefaultsConfiguration} giữ nguyên {@code NoOpPushNotificationSender}.
 *
 * <p>Truy vấn bảng {@code user_device_tokens} qua {@link JdbcTemplate} và bỏ qua an toàn nếu bảng chưa
 * tồn tại trên DB dev cũ (SQLSTATE {@code 42P01}), đảm bảo best-effort và không bao giờ ném lỗi ra ngoài.
 */
@Service
@ConditionalOnExpression(
        "!'${FIREBASE_CREDENTIALS_JSON:}'.isBlank() || !'${FIREBASE_CREDENTIALS_PATH:}'.isBlank()"
)
public class FirebasePushService implements PushNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(FirebasePushService.class);
    private static final int DELETE_BATCH_SIZE = 200;

    private final JdbcTemplate jdbcTemplate;
    private final FcmMulticastClient multicastClient;

    public FirebasePushService(JdbcTemplate jdbcTemplate, FcmMulticastClient multicastClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.multicastClient = multicastClient;
    }

    @Override
    public void send(Long userId, String title, String body, Map<String, String> data) {
        if (userId == null) {
            return;
        }
        try {
            List<String> tokens = fetchDeviceTokensForUser(userId);
            if (tokens.isEmpty()) {
                return;
            }
            FcmMulticastClient.FcmMulticastResult result = multicastClient.sendMulticast(tokens, title, body, data);
            if (result != null && result.staleTokens() != null && !result.staleTokens().isEmpty()) {
                deleteStaleDeviceTokens(result.staleTokens());
            }
        } catch (Exception e) {
            log.warn("Failed to dispatch push notification to user {}: {}", userId, e.getMessage());
        }
    }

    public int sendPushNotification(List<String> deviceTokens, String title, String body, Map<String, String> data) {
        if (deviceTokens == null || deviceTokens.isEmpty()) {
            return 0;
        }
        try {
            FcmMulticastClient.FcmMulticastResult result =
                    multicastClient.sendMulticast(deviceTokens, title, body, data);
            if (result == null) {
                return 0;
            }
            if (result.staleTokens() != null && !result.staleTokens().isEmpty()) {
                deleteStaleDeviceTokens(result.staleTokens());
            }
            return result.successCount();
        } catch (Exception e) {
            log.warn("Failed to dispatch direct push notification: {}", e.getMessage());
            return 0;
        }
    }

    List<String> fetchDeviceTokensForUser(Long userId) {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT fcm_token FROM user_device_tokens WHERE user_id = ?",
                    String.class,
                    userId
            );
        } catch (DataAccessException e) {
            if (isUndefinedTableError(e)) {
                log.debug("Table user_device_tokens does not exist; skipping FCM push lookup.");
                return List.of();
            }
            log.warn("Failed to query device tokens for user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    void deleteStaleDeviceTokens(List<String> staleTokens) {
        List<String> cleaned = staleTokens.stream()
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .toList();
        if (cleaned.isEmpty()) {
            return;
        }
        try {
            for (int start = 0; start < cleaned.size(); start += DELETE_BATCH_SIZE) {
                List<String> chunk = cleaned.subList(start, Math.min(start + DELETE_BATCH_SIZE, cleaned.size()));
                String placeholders = String.join(",", Collections.nCopies(chunk.size(), "?"));
                String sql = "DELETE FROM user_device_tokens WHERE fcm_token IN (" + placeholders + ")";
                jdbcTemplate.update(sql, chunk.toArray());
            }
        } catch (DataAccessException e) {
            if (isUndefinedTableError(e)) {
                log.debug("Table user_device_tokens does not exist; skipping stale token cleanup.");
                return;
            }
            log.warn("Failed to prune stale device tokens: {}", e.getMessage());
        }
    }

    static boolean isUndefinedTableError(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getCause()) {
            if (current instanceof SQLException sqlEx && "42P01".equals(sqlEx.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
