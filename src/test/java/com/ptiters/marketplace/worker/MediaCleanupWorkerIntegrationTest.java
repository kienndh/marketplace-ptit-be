package com.ptiters.marketplace.worker;

import com.ptiters.marketplace.integration.LocalMediaStorage;
import com.ptiters.marketplace.integration.MediaStorage;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class MediaCleanupWorkerIntegrationTest {

    record DestroyCall(String storageKey, String resourceType) {
    }

    static class RecordingMediaStorage extends LocalMediaStorage {
        final List<DestroyCall> destroyCalls = new CopyOnWriteArrayList<>();
        final Map<String, Object> behaviorByKey = new ConcurrentHashMap<>();

        RecordingMediaStorage() {
            super(LocalMediaStorage.inTempDirectory().getBaseDir());
        }

        void reset() {
            destroyCalls.clear();
            behaviorByKey.clear();
        }

        @Override
        public boolean destroyAsset(String publicId, String resourceType) {
            destroyCalls.add(new DestroyCall(publicId, resourceType));
            Object behavior = behaviorByKey.get(publicId);
            if (behavior instanceof RuntimeException re) {
                throw re;
            }
            if (behavior instanceof Boolean bool) {
                return bool;
            }
            return true;
        }
    }

    @TestConfiguration
    static class FakeMediaStorageConfig {
        @Bean
        @Primary
        MediaStorage recordingMediaStorage() {
            return new RecordingMediaStorage();
        }
    }

    @Autowired
    private MediaCleanupWorker worker;

    @Autowired
    private MediaCleanupScheduler scheduler;

    @Autowired
    private MediaCleanupJobRepository repository;

    @Autowired
    private RecordingMediaStorage recordingMediaStorage;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void verifyTestDatabaseSafety() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT current_database()")) {
            assertTrue(rs.next());
            assertTrue(rs.getString(1).matches("marketplace_java_test(_[a-z0-9]+)?"),
                    "Safety violation: test must run on marketplace_java_test*");
        }
        recordingMediaStorage.reset();
        jdbc.update("DELETE FROM media_cleanup_jobs");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM media_cleanup_jobs");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM media_cleanup_jobs", Integer.class));
    }

    @Test
    @DisplayName("Scheduler nền mặc định TẮT khi không cấu hình MEDIA_CLEANUP_ENABLED, và BẬT khi cấu hình true")
    void schedulingIsDisabledByDefault() {
        assertTrue(applicationContext.getBeansOfType(MediaCleanupSchedulingConfiguration.class).isEmpty());

        MediaCleanupWorker stubWorker = Mockito.mock(MediaCleanupWorker.class);
        Mockito.when(stubWorker.runCleanupSweep()).thenReturn(SweepResult.empty());
        EntityManagerFactory mockEmf = Mockito.mock(EntityManagerFactory.class);

        new ApplicationContextRunner()
                .withPropertyValues("MEDIA_CLEANUP_ENABLED=true")
                .withBean(EntityManagerFactory.class, () -> mockEmf)
                .withBean(MediaCleanupWorker.class, () -> stubWorker)
                .withUserConfiguration(MediaCleanupSchedulingConfiguration.class)
                .run(ctx -> {
                    assertNotNull(ctx);
                    assertFalse(ctx.getBeansOfType(MediaCleanupSchedulingConfiguration.class).isEmpty());
                });
    }

    @Test
    @DisplayName("enqueue() idempotent theo UNIQUE (storage_key, resource_type)")
    void enqueueIsIdempotentOnDuplicateKeyAndType() {
        scheduler.enqueue("student-marketplace/dev/conversations/conv-1/file1", "IMAGE", Duration.ZERO);
        scheduler.enqueue("student-marketplace/dev/conversations/conv-1/file1", "image", Duration.ZERO);

        assertEquals(1, repository.count());
        MediaCleanupJob job = repository.findAll().get(0);
        assertEquals("image", job.getResourceType());
        assertEquals(0, job.getAttemptCount());
    }

    @Test
    @DisplayName("runCleanupSweep() xóa job khi destroy thành công và giữ job chưa đến hạn")
    void sweepDeletesSucceededAndSkipsFutureJobs() {
        scheduler.enqueue("key/due-ok", "image", Duration.ZERO);
        scheduler.enqueue("key/due-video-ok", "video", Duration.ZERO);
        scheduler.enqueue("key/future-not-due", "image", Duration.ofMinutes(10));

        SweepResult res = worker.runCleanupSweep();
        assertEquals(2, res.candidates());
        assertEquals(2, res.changed());
        assertEquals(0, res.failed());

        assertEquals(2, recordingMediaStorage.destroyCalls.size());
        assertEquals(List.of("key/future-not-due"),
                repository.findAll().stream().map(MediaCleanupJob::getStorageKey).toList());
    }

    @Test
    @DisplayName("runCleanupSweep() tăng attempt_count, tính backoff và sanitize last_error khi destroy thất bại")
    void sweepRetriesWithBackoffAndSanitizesSecretsOnFailure() {
        recordingMediaStorage.behaviorByKey.put("key/non-ok", Boolean.FALSE);
        recordingMediaStorage.behaviorByKey.put("key/throws-secret",
                new RuntimeException("Upload failed at https://api.cloudinary.com/v1_1/demo?api_secret=topsecret123 Bearer eyJhbGciOiJIUzI1NiJ9"));

        scheduler.enqueue("key/non-ok", "image", Duration.ZERO);
        scheduler.enqueue("key/throws-secret", "raw", Duration.ZERO);

        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
        SweepResult res = worker.runCleanupSweep();
        assertEquals(2, res.candidates());
        assertEquals(0, res.changed());
        assertEquals(2, res.failed());

        MediaCleanupJob nonOkJob = repository.findAll().stream()
                .filter(j -> "key/non-ok".equals(j.getStorageKey()))
                .findFirst().orElseThrow();
        assertEquals(1, nonOkJob.getAttemptCount());
        assertEquals("Cloudinary destroy returned non-ok result", nonOkJob.getLastError());
        assertTrue(!nonOkJob.getNextRetryAt().isBefore(before.plusSeconds(55)));

        MediaCleanupJob secretJob = repository.findAll().stream()
                .filter(j -> "key/throws-secret".equals(j.getStorageKey()))
                .findFirst().orElseThrow();
        assertEquals(1, secretJob.getAttemptCount());
        assertNotNull(secretJob.getLastError());
        assertFalse(secretJob.getLastError().contains("topsecret123"));
        assertFalse(secretJob.getLastError().contains("https://"));
        assertFalse(secretJob.getLastError().contains("eyJhbGciOiJIUzI1NiJ9"));
        assertTrue(secretJob.getLastError().contains("[REDACTED]"));
    }

    @Test
    @DisplayName("calculateNextRetryAt() tuân thủ lịch backoff Python [60, 300, 900, 3600, 21600, 86400]")
    void backoffScheduleMatchesPython() {
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        assertEquals(base.plusSeconds(60), MediaCleanupWorker.calculateNextRetryAt(1, base));
        assertEquals(base.plusSeconds(300), MediaCleanupWorker.calculateNextRetryAt(2, base));
        assertEquals(base.plusSeconds(900), MediaCleanupWorker.calculateNextRetryAt(3, base));
        assertEquals(base.plusSeconds(3600), MediaCleanupWorker.calculateNextRetryAt(4, base));
        assertEquals(base.plusSeconds(21600), MediaCleanupWorker.calculateNextRetryAt(5, base));
        assertEquals(base.plusSeconds(86400), MediaCleanupWorker.calculateNextRetryAt(6, base));
        assertEquals(base.plusSeconds(86400), MediaCleanupWorker.calculateNextRetryAt(10, base));
    }

    @Test
    @DisplayName("sanitizeExternalError() cắt ngắn tối đa 500 ký tự")
    void sanitizeExternalErrorTruncatesAt500() {
        String longMsg = "X".repeat(700);
        String sanitized = MediaCleanupWorker.sanitizeExternalError(longMsg, 500);
        assertEquals(500, sanitized.length());
        assertTrue(sanitized.endsWith("..."));
    }
}
