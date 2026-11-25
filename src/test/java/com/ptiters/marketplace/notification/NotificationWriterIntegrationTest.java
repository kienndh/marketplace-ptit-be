package com.ptiters.marketplace.notification;

import com.ptiters.marketplace.realtime.UserEventPublisher;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserRepository;
import com.ptiters.marketplace.user.UserRole;
import com.ptiters.marketplace.user.UserStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ghi thật vào PostgreSQL marketplace_java_test (commit thật), dọn sạch trong @AfterEach. */
@SpringBootTest
@ActiveProfiles("test")
class NotificationWriterIntegrationTest {

    record Pushed(Long userId, String title, String body, Map<String, String> data) {
    }

    static final List<Pushed> PUSHED = new CopyOnWriteArrayList<>();
    static final List<Map<String, Object>> EVENTS = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class RecordingConfig {
        @Bean
        @Primary
        PushNotificationSender recordingPushSender() {
            return (userId, title, body, data) -> PUSHED.add(new Pushed(userId, title, body, data));
        }

        @Bean
        @Primary
        UserEventPublisher recordingEventPublisher() {
            return new UserEventPublisher() {
                @Override
                public void publishUserEvent(Long userId, Map<String, Object> eventData) {
                    EVENTS.add(Map.of("userId", userId, "event", eventData));
                }

                @Override
                public void revokeUserSessions(Long userId) {
                }
            };
        }
    }

    @Autowired
    private NotificationWriter writer;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TransactionTemplate tx;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private EntityManager entityManager;

    private final List<Long> createdUserIds = new ArrayList<>();

    @BeforeEach
    void safetyBoundary() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT current_database()")) {
            rs.next();
            assertTrue(rs.getString(1).matches("marketplace_java_test(_[a-z0-9]+)?"), "Chỉ được ghi trên marketplace_java_test*");
        }
        PUSHED.clear();
        EVENTS.clear();
    }

    @AfterEach
    void cleanUp() {
        for (Long id : createdUserIds) {
            jdbc.update("DELETE FROM notifications WHERE user_id = ?", id);
            jdbc.update("DELETE FROM users WHERE user_id = ?", id);
        }
        createdUserIds.clear();
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class));
    }

    private User newUser(UserRole role, UserStatus status) {
        User u = new User();
        u.setEmail("nw_" + UUID.randomUUID() + "@synthetic.local");
        u.setFullName("Notification Writer Test");
        u.setRole(role);
        u.setStatus(status);
        User saved = userRepository.saveAndFlush(u);
        createdUserIds.add(saved.getUserId());
        return saved;
    }

    @Test
    @DisplayName("create() ghi đúng cột vào DB, dispatch realtime + push chỉ SAU khi commit")
    void createPersistsAndDispatchesAfterCommit() {
        User user = tx.execute(s -> newUser(UserRole.STUDENT, UserStatus.ACTIVE));

        Notification[] holder = new Notification[1];
        tx.executeWithoutResult(s -> {
            holder[0] = writer.create(user.getUserId(), NotificationType.NEW_ORDER,
                    "Bạn có đơn hàng mới", "Đơn #42", "order", 42L);
            assertTrue(PUSHED.isEmpty(), "Chưa được push trước khi commit");
            assertTrue(EVENTS.isEmpty(), "Chưa được publish realtime trước khi commit");
        });

        Notification saved = holder[0];
        assertNotNull(saved.getNotificationId());
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT type, title, body, ref_type, ref_id, is_read, created_at FROM notifications WHERE notification_id = ?",
                saved.getNotificationId());
        assertEquals("NEW_ORDER", row.get("type"));
        assertEquals("Bạn có đơn hàng mới", row.get("title"));
        assertEquals("Đơn #42", row.get("body"));
        assertEquals("order", row.get("ref_type"));
        assertEquals(42L, ((Number) row.get("ref_id")).longValue());
        assertEquals(Boolean.FALSE, row.get("is_read"));
        assertNotNull(row.get("created_at"));

        assertEquals(1, PUSHED.size());
        Pushed pushed = PUSHED.get(0);
        assertEquals(user.getUserId(), pushed.userId());
        assertEquals(Map.of("notification_id", String.valueOf(saved.getNotificationId()), "type", "NEW_ORDER",
                "ref_type", "order", "ref_id", "42"), pushed.data());
        assertEquals(1, EVENTS.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) EVENTS.get(0).get("event");
        assertEquals("NOTIFICATION_CREATED", event.get("type"));
    }

    @Test
    @DisplayName("Transaction rollback thì không ghi DB và không push/realtime")
    void rollbackSuppressesDispatch() {
        User user = tx.execute(s -> newUser(UserRole.STUDENT, UserStatus.ACTIVE));
        try {
            tx.executeWithoutResult(s -> {
                writer.create(user.getUserId(), NotificationType.SYSTEM, "x", null, null, null);
                throw new IllegalStateException("force rollback");
            });
        } catch (IllegalStateException expected) {
            // ok
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ?", Integer.class,
                user.getUserId()));
        assertTrue(PUSHED.isEmpty());
        assertTrue(EVENTS.isEmpty());
    }

    @Test
    @DisplayName("notifyAdmins() chỉ gửi SYSTEM cho ADMIN đang ACTIVE")
    void notifyAdminsTargetsActiveAdminsOnly() {
        User admin = tx.execute(s -> newUser(UserRole.ADMIN, UserStatus.ACTIVE));
        User bannedAdmin = tx.execute(s -> newUser(UserRole.ADMIN, UserStatus.BANNED));
        User student = tx.execute(s -> newUser(UserRole.STUDENT, UserStatus.ACTIVE));

        List<Notification> created = tx.execute(s -> writer.notifyAdmins("Có phản hồi mới", "Nội dung", "feedback", 7L));

        List<Long> recipients = created.stream().map(Notification::getUserId).toList();
        assertTrue(recipients.contains(admin.getUserId()));
        assertFalse(recipients.contains(bannedAdmin.getUserId()));
        assertFalse(recipients.contains(student.getUserId()));
        for (Notification n : created) {
            assertEquals(NotificationType.SYSTEM, n.getType());
            createdUserIds.add(n.getUserId());
        }
        assertEquals(created.size(), PUSHED.size());
        // dọn các admin khác (nếu có) đã phát sinh notification trong test DB
        created.stream().map(Notification::getUserId).distinct()
                .forEach(id -> jdbc.update("DELETE FROM notifications WHERE user_id = ?", id));
        createdUserIds.removeIf(id -> !List.of(admin.getUserId(), bannedAdmin.getUserId(), student.getUserId()).contains(id));
    }
}
