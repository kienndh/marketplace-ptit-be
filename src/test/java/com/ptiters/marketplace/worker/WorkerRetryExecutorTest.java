package com.ptiters.marketplace.worker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Đối chiếu hành vi với {@code app/workers/retry.py}. */
class WorkerRetryExecutorTest {

    private final List<Long> sleeps = new ArrayList<>();
    private final WorkerRetryExecutor executor = new WorkerRetryExecutor(sleeps::add);

    @Test
    @DisplayName("Phân loại: deadlock / serialization / mất kết nối là tạm thời")
    void classifiesTransientErrors() {
        assertTrue(WorkerRetryExecutor.isTransientDbError(new SQLException("deadlock", "40P01")));
        assertTrue(WorkerRetryExecutor.isTransientDbError(new SQLException("serialization", "40001")));
        assertTrue(WorkerRetryExecutor.isTransientDbError(new SQLException("conn", "08006")));
        assertTrue(WorkerRetryExecutor.isTransientDbError(new CannotAcquireLockException("lock")));
        assertTrue(WorkerRetryExecutor.isTransientDbError(
                new RuntimeException("wrapped", new SQLException("shutdown", "57P01"))));
    }

    @Test
    @DisplayName("Phân loại: integrity / data / logic KHÔNG bao giờ là tạm thời")
    void neverTreatsIntegrityAsTransient() {
        assertFalse(WorkerRetryExecutor.isTransientDbError(new SQLIntegrityConstraintViolationException("dup", "23505")));
        assertFalse(WorkerRetryExecutor.isTransientDbError(new SQLException("bad data", "22001")));
        assertFalse(WorkerRetryExecutor.isTransientDbError(new DataIntegrityViolationException("dup")));
        assertFalse(WorkerRetryExecutor.isTransientDbError(new IllegalStateException("domain error")));
        // lỗi tạm thời nhưng nguyên nhân gốc là vi phạm ràng buộc => không retry
        assertFalse(WorkerRetryExecutor.isTransientDbError(
                new CannotAcquireLockException("x", new SQLException("dup", "23505"))));
    }

    @Test
    @DisplayName("Retry tới khi thành công với backoff luỹ thừa 0.5s, 1s")
    void retriesTransientThenSucceeds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String result = executor.executeWithRetry(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new SQLException("deadlock", "40P01");
            }
            return "ok";
        }, 3, 500, "test-op");
        assertEquals("ok", result);
        assertEquals(3, calls.get());
        assertEquals(List.of(500L, 1000L), sleeps);
    }

    @Test
    @DisplayName("Vượt maxRetries thì ném lại lỗi gốc (tổng maxRetries + 1 lần gọi)")
    void stopsAfterMaxRetries() {
        AtomicInteger calls = new AtomicInteger();
        SQLException thrown = assertThrows(SQLException.class, () -> executor.executeWithRetry(() -> {
            calls.incrementAndGet();
            throw new SQLException("conn lost", "08003");
        }, 2, 100, "test-op"));
        assertEquals("08003", thrown.getSQLState());
        assertEquals(3, calls.get());
        assertEquals(List.of(100L, 200L), sleeps);
    }

    @Test
    @DisplayName("Lỗi không tạm thời thất bại ngay, không ngủ")
    void nonTransientFailsImmediately() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(DataIntegrityViolationException.class, () -> executor.executeWithRetry(() -> {
            calls.incrementAndGet();
            throw new DataIntegrityViolationException("dup");
        }, 3, 500, "test-op"));
        assertEquals(1, calls.get());
        assertTrue(sleeps.isEmpty());
    }
}
