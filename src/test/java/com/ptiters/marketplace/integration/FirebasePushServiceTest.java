package com.ptiters.marketplace.integration;

import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserRepository;
import com.ptiters.marketplace.user.UserRole;
import com.ptiters.marketplace.user.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class FirebasePushServiceTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DataSource dataSource;

    private final List<Long> createdUserIds = new ArrayList<>();

    @BeforeEach
    void safetyBoundary() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT current_database()")) {
            assertTrue(rs.next());
            assertTrue(rs.getString(1).matches("marketplace_java_test(_[a-z0-9]+)?"),
                    "Safety violation: test must run on marketplace_java_test*");
        }
    }

    @AfterEach
    void cleanUp() {
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM user_device_tokens WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
        }
        createdUserIds.clear();
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM user_device_tokens", Integer.class));
    }

    @Test
    @DisplayName("FirebasePushService chỉ kích hoạt khi có FIREBASE_CREDENTIALS_JSON hoặc PATH")
    void conditionalActivationMatchesConfiguration() {
        new ApplicationContextRunner()
                .withBean(JdbcTemplate.class, () -> jdbc)
                .withUserConfiguration(FirebaseAdminFcmMulticastClient.class, FirebasePushService.class)
                .run(ctx -> assertFalse(ctx.containsBean("firebasePushService")));

        new ApplicationContextRunner()
                .withPropertyValues("FIREBASE_CREDENTIALS_JSON={\"invalid\":\"but-non-blank\"}")
                .withBean(JdbcTemplate.class, () -> jdbc)
                .withUserConfiguration(FirebaseAdminFcmMulticastClient.class, FirebasePushService.class)
                .run(ctx -> {
                    assertTrue(ctx.containsBean("firebasePushService"));
                    assertTrue(ctx.containsBean("firebaseAdminFcmMulticastClient"));
                });
    }

    @Test
    @DisplayName("send() đọc token từ user_device_tokens, gửi multicast bằng client giả và xóa token stale")
    void sendsToRegisteredTokensAndPrunesStaleTokensOnPostgres() {
        User user = new User();
        user.setEmail("fcm_" + UUID.randomUUID() + "@synthetic.local");
        user.setFullName("FCM Test User");
        user.setRole(UserRole.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
        User saved = userRepository.saveAndFlush(user);
        createdUserIds.add(saved.getUserId());

        jdbc.update("INSERT INTO user_device_tokens (user_id, fcm_token, device_info) VALUES (?, ?, ?)",
                saved.getUserId(), "valid-token-1", "Chrome");
        jdbc.update("INSERT INTO user_device_tokens (user_id, fcm_token, device_info) VALUES (?, ?, ?)",
                saved.getUserId(), "stale-token-2", "Safari");

        List<List<String>> recordedTokenBatches = new ArrayList<>();
        FcmMulticastClient fakeClient = (tokens, title, body, data) -> {
            recordedTokenBatches.add(List.copyOf(tokens));
            return new FcmMulticastClient.FcmMulticastResult(1, List.of("stale-token-2"));
        };

        FirebasePushService service = new FirebasePushService(jdbc, fakeClient);
        service.send(saved.getUserId(), "Đơn hàng mới", "Bạn có đơn #10", Map.of("notification_id", "10"));

        assertEquals(1, recordedTokenBatches.size());
        assertTrue(recordedTokenBatches.get(0).containsAll(List.of("valid-token-1", "stale-token-2")));

        List<String> remaining = jdbc.queryForList(
                "SELECT fcm_token FROM user_device_tokens WHERE user_id = ?",
                String.class,
                saved.getUserId());
        assertEquals(List.of("valid-token-1"), remaining);
    }

    @Test
    @DisplayName("send() không ném lỗi khi bảng user_device_tokens không tồn tại (SQLSTATE 42P01) hoặc client giả ném lỗi")
    void handlesMissingTableAndClientExceptionsGracefully() {
        JdbcTemplate missingTableJdbc = mock(JdbcTemplate.class);
        when(missingTableJdbc.queryForList(anyString(), eqClassString(), any()))
                .thenThrow(new org.springframework.jdbc.BadSqlGrammarException(
                        "query", "SELECT", new SQLException("relation \"user_device_tokens\" does not exist", "42P01")));

        FcmMulticastClient throwingClient = (tokens, title, body, data) -> {
            throw new RuntimeException("FCM unreachable");
        };

        FirebasePushService missingTableService = new FirebasePushService(missingTableJdbc, throwingClient);
        assertDoesNotThrow(() -> missingTableService.send(1L, "Test", "Body", Map.of()));
        assertEquals(0, missingTableService.sendPushNotification(List.of("t1"), "Test", "Body", Map.of()));
        assertNotNull(FirebaseAdminFcmMulticastClient.buildMulticastMessage(
                List.of("t1"), "Title", "Body", Map.of("notification_id", "99")));
    }

    private static Class<String> eqClassString() {
        return org.mockito.ArgumentMatchers.eq(String.class);
    }
}
