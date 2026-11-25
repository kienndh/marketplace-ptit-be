package com.ptiters.marketplace.configuration;

import com.ptiters.marketplace.notification.NotificationType;
import com.ptiters.marketplace.notification.NotificationWriter;
import com.ptiters.marketplace.trackc.TrackCFixtures;
import com.ptiters.marketplace.trackc.TrackCTestConfig;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JVM chạy UTC (surefire argLine) và timestamp Hibernate ghi xuống DB đúng UTC — kiểm bằng SQL thô,
 * để dữ liệu dùng chung với backend Python (naive UTC) không lệch múi giờ.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TrackCTestConfig.class)
class UtcTimestampIntegrationTest {

    @Autowired TrackCFixtures fx;
    @Autowired NotificationWriter notificationWriter;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void tearDown() {
        fx.cleanAll();
    }

    @Test
    @DisplayName("JVM mặc định UTC; notifications.created_at ghi qua Hibernate khớp UTC khi đọc bằng SQL thô")
    void timestampsAreStoredAsUtc() {
        assertEquals("UTC", TimeZone.getDefault().getID(), "surefire phải chạy với -Duser.timezone=UTC");
        User u = fx.user("utc", UserRole.STUDENT);
        notificationWriter.create(u.getUserId(), NotificationType.SYSTEM, "t", "b", null, null);

        // Đọc thô bằng SQL: ép cột timestamp-không-múi-giờ thành chuỗi, không qua Hibernate/JDBC time zone
        String raw = jdbc.queryForObject("SELECT created_at::text FROM notifications WHERE user_id = ?", String.class, u.getUserId());
        LocalDateTime stored = LocalDateTime.parse(raw.replace(' ', 'T'));
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        long diff = Math.abs(Duration.between(stored, nowUtc).toSeconds());
        assertTrue(diff < 120, "created_at (" + stored + ") phải ≈ UTC hiện tại (" + nowUtc + "), lệch " + diff + "s");
    }
}
