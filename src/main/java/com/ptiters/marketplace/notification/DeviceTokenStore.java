package com.ptiters.marketplace.notification;

import com.ptiters.marketplace.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Lưu trữ và truy vấn FCM device token qua {@link JdbcTemplate} thay cho JPA entity,
 * vì bảng {@code user_device_tokens} chưa tồn tại trên {@code marketplace_java_dev} (Alembic b8c9d0e1f2a3).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceTokenStore {

    private final JdbcTemplate jdbcTemplate;

    public boolean isTableAvailable() {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT to_regclass('user_device_tokens') IS NOT NULL",
                Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }

    public void upsertToken(Long userId, String fcmToken, String deviceInfo) {
        if (!isTableAvailable()) {
            log.warn("Bảng user_device_tokens chưa tồn tại; bỏ qua đăng ký FCM token cho user {}", userId);
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Dịch vụ thông báo đẩy chưa sẵn sàng (chưa di trú bảng user_device_tokens)."
            );
        }
        String sql = """
                INSERT INTO user_device_tokens (user_id, fcm_token, device_info, created_at, last_seen_at)
                VALUES (?, ?, ?, NOW(), NOW())
                ON CONFLICT (fcm_token) DO UPDATE
                SET user_id = EXCLUDED.user_id,
                    device_info = EXCLUDED.device_info,
                    last_seen_at = NOW()
                """;
        jdbcTemplate.update(sql, userId, fcmToken, deviceInfo);
    }

    public boolean deleteToken(Long userId, String fcmToken) {
        if (!isTableAvailable()) {
            log.warn("Bảng user_device_tokens chưa tồn tại; bỏ qua hủy đăng ký FCM token cho user {}", userId);
            return false;
        }
        int deleted = jdbcTemplate.update(
                "DELETE FROM user_device_tokens WHERE user_id = ? AND fcm_token = ?",
                userId,
                fcmToken
        );
        return deleted > 0;
    }

    public List<String> getTokensForUser(Long userId) {
        if (!isTableAvailable()) {
            log.warn("Bảng user_device_tokens chưa tồn tại; trả về danh sách token rỗng cho user {}", userId);
            return List.of();
        }
        return jdbcTemplate.queryForList(
                "SELECT fcm_token FROM user_device_tokens WHERE user_id = ?",
                String.class,
                userId
        );
    }

    public void deleteStaleTokens(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        if (!isTableAvailable()) {
            log.warn("Bảng user_device_tokens chưa tồn tại; bỏ qua xóa stale token");
            return;
        }
        String placeholders = String.join(",", Collections.nCopies(tokens.size(), "?"));
        jdbcTemplate.update(
                "DELETE FROM user_device_tokens WHERE fcm_token IN (" + placeholders + ")",
                tokens.toArray()
        );
    }
}
