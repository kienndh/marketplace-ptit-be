package com.ptiters.marketplace.dailyneed;

import com.jayway.jsonpath.JsonPath;
import com.ptiters.marketplace.product.ProductTestFixtures;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserAuthIdentityRepository;
import com.ptiters.marketplace.user.UserRepository;
import com.ptiters.marketplace.user.UserRole;
import com.ptiters.marketplace.user.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=3",
        "spring.datasource.hikari.minimum-idle=1"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DailyNeedControllerIntegrationTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        @Primary
        public JwtDecoder testJwtDecoder() {
            return ProductTestFixtures.createTestJwtDecoder();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DailyNeedRepository dailyNeedRepository;

    @Autowired
    private DailyJournalCheckinRepository checkinRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuthIdentityRepository userAuthIdentityRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    private User userA;
    private User userB;
    private User bannedUser;
    private String tokenA;
    private String tokenB;
    private String bannedToken;

    @BeforeEach
    void setUp() throws Exception {
        verifySafetyBoundary();
        cleanDatabase();

        userA = ProductTestFixtures.createUser(userRepository, "dn_a@test.com", "Nguyen Khac Thinh", UserRole.STUDENT);
        userA.setPhone("0912345678");
        userA.setBankBin("970422");
        userA.setBankAccountNumber("0123456789");
        userRepository.saveAndFlush(userA);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, userA, "dn_a_uid");
        tokenA = ProductTestFixtures.createToken("dn_a_uid", userA.getEmail());

        userB = ProductTestFixtures.createUser(userRepository, "dn_b@test.com", "Tran Minh Anh", UserRole.STUDENT);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, userB, "dn_b_uid");
        tokenB = ProductTestFixtures.createToken("dn_b_uid", userB.getEmail());

        bannedUser = ProductTestFixtures.createUser(userRepository, "dn_banned@test.com", "Banned User", UserRole.STUDENT);
        bannedUser.setStatus(UserStatus.BANNED);
        userRepository.saveAndFlush(bannedUser);
        ProductTestFixtures.createAuthIdentity(userAuthIdentityRepository, bannedUser, "dn_banned_uid");
        bannedToken = ProductTestFixtures.createToken("dn_banned_uid", bannedUser.getEmail());
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void verifySafetyBoundary() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT current_database()")) {
            assertTrue(rs.next());
            String dbName = rs.getString(1);
            assertTrue(dbName.matches("marketplace_java_test(_[a-z0-9]+)?"),
                    "SAFETY VIOLATION: tests must only run on marketplace_java_test*: " + dbName);
        }
        assertEquals("false", environment.getProperty("spring.flyway.enabled"));
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
    }

    private void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM daily_journal_checkins");
        jdbcTemplate.update("DELETE FROM daily_needs");
        jdbcTemplate.update("DELETE FROM user_auth_identities");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    @DisplayName("Auth & Public route boundary: /me/* trả 401/403, /today, /recent, /users/{publicId}/today công khai")
    void testAuthAndPublicBoundaries() throws Exception {
        mockMvc.perform(get("/api/v1/daily-needs/me/today")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/daily-needs/me/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/daily-needs/me/today")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"test\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/daily-needs/me/today")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/daily-needs/me/today")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bannedToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("User account is banned."));

        // Public endpoints without token
        mockMvc.perform(get("/api/v1/daily-needs/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        mockMvc.perform(get("/api/v1/daily-needs/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        mockMvc.perform(get("/api/v1/daily-needs/users/{publicId}/today", userA.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(content().string("null"));

        // Non-UUID on public user today -> 422
        mockMvc.perform(get("/api/v1/daily-needs/users/not-a-uuid/today"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("CRUD Upsert/Edit/Delete + bảo lưu check-in & streak khi xóa + repost không tăng đúp")
    void testUpsertEditDeleteAndStreakPreservation() throws Exception {
        LocalDate vnToday = DailyNeedQueryRepository.getVietnamToday();

        // 1. Initial me/today returns JSON null
        mockMvc.perform(get("/api/v1/daily-needs/me/today")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(content().string("null"));

        // 2. Validation 422 on empty / whitespace / >120 chars
        mockMvc.perform(put("/api/v1/daily-needs/me/today")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"\"}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(put("/api/v1/daily-needs/me/today")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"   \\n\\t  \"}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(put("/api/v1/daily-needs/me/today")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"" + "A".repeat(121) + "\"}"))
                .andExpect(status().isUnprocessableEntity());

        // 3. First publish today -> checkin_created = true, multiline whitespace collapsed
        MvcResult put1 = mockMvc.perform(put("/api/v1/daily-needs/me/today")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Cần mua\\n  giáo trình   Giải tích 1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Cần mua giáo trình Giải tích 1"))
                .andExpect(jsonPath("$.note_date").value(vnToday.toString()))
                .andExpect(jsonPath("$.checkin_created").value(true))
                .andExpect(jsonPath("$.author.public_id").value(userA.getPublicId().toString()))
                .andExpect(jsonPath("$.author.user_id").doesNotExist())
                .andExpect(jsonPath("$.author.email").doesNotExist())
                .andExpect(jsonPath("$.author.phone").doesNotExist())
                .andExpect(jsonPath("$.author.bank_bin").doesNotExist())
                .andReturn();

        Long needId1 = ((Number) JsonPath.read(put1.getResponse().getContentAsString(), "$.daily_need_id")).longValue();

        // 4. Same day update -> same daily_need_id, checkin_created = false
        mockMvc.perform(put("/api/v1/daily-needs/me/today")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Cần mua giáo trình Spring Boot\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daily_need_id").value(needId1))
                .andExpect(jsonPath("$.content").value("Cần mua giáo trình Spring Boot"))
                .andExpect(jsonPath("$.checkin_created").value(false));

        // 5. Summary shows streak = 1
        mockMvc.perform(get("/api/v1/daily-needs/me/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.has_journal_today").value(true))
                .andExpect(jsonPath("$.has_checked_in_today").value(true))
                .andExpect(jsonPath("$.has_posted_today").value(true))
                .andExpect(jsonPath("$.streak_count").value(1))
                .andExpect(jsonPath("$.today_journal.daily_need_id").value(needId1));

        // 6. Delete today's journal -> content removed, check-in & streak preserved!
        mockMvc.perform(delete("/api/v1/daily-needs/me/today")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        // Idempotent second delete -> 204
        mockMvc.perform(delete("/api/v1/daily-needs/me/today")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/daily-needs/me/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.has_journal_today").value(false))
                .andExpect(jsonPath("$.has_checked_in_today").value(true))
                .andExpect(jsonPath("$.has_posted_today").value(true))
                .andExpect(jsonPath("$.streak_count").value(1))
                .andExpect(jsonPath("$.today_journal").value(nullValue()));

        // Public endpoints no longer see deleted journal
        mockMvc.perform(get("/api/v1/daily-needs/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
        mockMvc.perform(get("/api/v1/daily-needs/users/{publicId}/today", userA.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(content().string("null"));

        // 7. Repost after delete -> checkin_created = false, streak remains 1
        mockMvc.perform(put("/api/v1/daily-needs/me/today")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Đăng lại trong ngày\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkin_created").value(false));

        mockMvc.perform(get("/api/v1/daily-needs/me/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streak_count").value(1));
    }

    @Test
    @DisplayName("Recursive CTE Streak: chuỗi hôm qua (chưa đăng hôm nay), đứt chuỗi khi bỏ ngày, chuỗi > 365 ngày")
    void testRecursiveCteStreakCalculation() throws Exception {
        LocalDate today = DailyNeedQueryRepository.getVietnamToday();

        // 1. Seed 4 consecutive days ending yesterday (today-4 .. today-1)
        for (int offset = 4; offset >= 1; offset--) {
            checkinRepository.saveAndFlush(DailyJournalCheckin.builder()
                    .userId(userA.getUserId())
                    .checkinDate(today.minusDays(offset))
                    .build());
        }

        // Before posting today: streak_count = 4, has_checked_in_today = false
        mockMvc.perform(get("/api/v1/daily-needs/me/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streak_count").value(4))
                .andExpect(jsonPath("$.has_checked_in_today").value(false))
                .andExpect(jsonPath("$.has_journal_today").value(false));

        // Post today -> streak becomes 5
        mockMvc.perform(put("/api/v1/daily-needs/me/today")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Ngày thứ 5\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkin_created").value(true));

        mockMvc.perform(get("/api/v1/daily-needs/me/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streak_count").value(5))
                .andExpect(jsonPath("$.has_checked_in_today").value(true));

        // 2. User B missed yesterday (only today-3, today-2) -> streak = 0
        for (int offset : List.of(3, 2)) {
            checkinRepository.saveAndFlush(DailyJournalCheckin.builder()
                    .userId(userB.getUserId())
                    .checkinDate(today.minusDays(offset))
                    .build());
        }
        mockMvc.perform(get("/api/v1/daily-needs/me/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streak_count").value(0));

        // 3. Streak > 365 days (370 consecutive days for userB ending today) -> 370 (no cap)
        jdbcTemplate.update("DELETE FROM daily_journal_checkins WHERE user_id = ?", userB.getUserId());
        for (int offset = 369; offset >= 0; offset--) {
            jdbcTemplate.update(
                    "INSERT INTO daily_journal_checkins (user_id, checkin_date, created_at) VALUES (?, ?, NOW())",
                    userB.getUserId(),
                    today.minusDays(offset)
            );
        }
        mockMvc.perform(get("/api/v1/daily-needs/me/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streak_count").value(370))
                .andExpect(jsonPath("$.has_checked_in_today").value(true));
    }

    @Test
    @DisplayName("Public /today, /recent (limit clamp & ordering), /users/{publicId}/today (active vs banned/yesterday)")
    void testPublicFeedsAndUserTodayEndpoint() throws Exception {
        LocalDate today = DailyNeedQueryRepository.getVietnamToday();
        LocalDate yesterday = today.minusDays(1);

        // Create 12 users with notes (11 today + 1 yesterday)
        List<User> extraUsers = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            User u = ProductTestFixtures.createUser(userRepository, "dn_feed_" + i + "@test.com", "Student " + i, UserRole.STUDENT);
            extraUsers.add(u);
            OffsetDateTime ts = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1).plusMinutes(i);
            LocalDate d = (i == 0) ? yesterday : today;
            jdbcTemplate.update(
                    "INSERT INTO daily_needs (user_id, note_date, content, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                    u.getUserId(), d, "Note #" + i, ts, ts
            );
        }

        // /today returns max 10 today notes, excludes yesterday (i=0), newest first (i=11..i=2)
        mockMvc.perform(get("/api/v1/daily-needs/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(10)))
                .andExpect(jsonPath("$.items[0].content").value("Note #11"))
                .andExpect(jsonPath("$.items[9].content").value("Note #2"));

        // /recent with limit=50 clamps to 10; limit=3 returns 3
        mockMvc.perform(get("/api/v1/daily-needs/recent").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(10)))
                .andExpect(jsonPath("$.items[0].content").value("Note #11"));

        mockMvc.perform(get("/api/v1/daily-needs/recent").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)));

        // /users/{publicId}/today for user with only yesterday's note -> 200 JSON null
        mockMvc.perform(get("/api/v1/daily-needs/users/{publicId}/today", extraUsers.get(0).getPublicId()))
                .andExpect(status().isOk())
                .andExpect(content().string("null"));

        // /users/{publicId}/today for user with today's note -> 200 DailyNeedPublicResponse
        mockMvc.perform(get("/api/v1/daily-needs/users/{publicId}/today", extraUsers.get(11).getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Note #11"))
                .andExpect(jsonPath("$.note_date").value(today.toString()))
                .andExpect(jsonPath("$.author.public_id").value(extraUsers.get(11).getPublicId().toString()))
                .andExpect(jsonPath("$.author.user_id").doesNotExist());

        // Banned user -> 404
        mockMvc.perform(get("/api/v1/daily-needs/users/{publicId}/today", bannedUser.getPublicId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Không tìm thấy người dùng."));

        // Unknown UUID -> 404
        mockMvc.perform(get("/api/v1/daily-needs/users/{publicId}/today", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DB Unique constraints & Concurrent PUT /me/today tạo đúng 1 check-in và không trùng lặp")
    void testUniqueConstraintsAndConcurrentUpsert() throws Exception {
        LocalDate today = DailyNeedQueryRepository.getVietnamToday();

        // 1. Direct DB unique constraint check on daily_needs(user_id, note_date)
        dailyNeedRepository.saveAndFlush(DailyNeed.builder()
                .userId(userA.getUserId())
                .noteDate(today)
                .content("First note")
                .build());
        assertThrows(DataIntegrityViolationException.class, () -> dailyNeedRepository.saveAndFlush(
                DailyNeed.builder()
                        .userId(userA.getUserId())
                        .noteDate(today)
                        .content("Duplicate note")
                        .build()
        ));

        // 2. Direct DB unique constraint check on daily_journal_checkins(user_id, checkin_date)
        checkinRepository.saveAndFlush(DailyJournalCheckin.builder()
                .userId(userA.getUserId())
                .checkinDate(today)
                .build());
        assertThrows(DataIntegrityViolationException.class, () -> checkinRepository.saveAndFlush(
                DailyJournalCheckin.builder()
                        .userId(userA.getUserId())
                        .checkinDate(today)
                        .build()
        ));

        // 3. Concurrent HTTP PUT /api/v1/daily-needs/me/today for userB (fresh user)
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Callable<Boolean>> tasks = List.of(0, 1).stream()
                .<Callable<Boolean>>map(idx -> () -> {
                    ready.countDown();
                    start.await();
                    MvcResult res = mockMvc.perform(put("/api/v1/daily-needs/me/today")
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"content\": \"Concurrent note " + idx + "\"}"))
                            .andExpect(status().isOk())
                            .andReturn();
                    return JsonPath.read(res.getResponse().getContentAsString(), "$.checkin_created");
                })
                .toList();

        List<Future<Boolean>> futures = new ArrayList<>();
        for (Callable<Boolean> task : tasks) {
            futures.add(pool.submit(task));
        }
        ready.await();
        start.countDown();

        List<Boolean> flags = new ArrayList<>();
        for (Future<Boolean> f : futures) {
            flags.add(f.get());
        }
        pool.shutdown();

        assertEquals(1, flags.stream().filter(Boolean::booleanValue).count(),
                "Exactly one concurrent PUT must report checkin_created=true");
        assertEquals(1, flags.stream().filter(b -> !b).count(),
                "Exactly one concurrent PUT must report checkin_created=false");

        Integer noteCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM daily_needs WHERE user_id = ? AND note_date = ?",
                Integer.class, userB.getUserId(), today);
        Integer checkinCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM daily_journal_checkins WHERE user_id = ? AND checkin_date = ?",
                Integer.class, userB.getUserId(), today);
        assertEquals(1, noteCount);
        assertEquals(1, checkinCount);
    }
}
