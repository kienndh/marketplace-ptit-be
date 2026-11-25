package com.ptiters.marketplace.worker;

import org.hibernate.exception.JDBCConnectionException;
import org.hibernate.exception.LockAcquisitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Thực thi tác vụ worker với retry có giới hạn CHỈ cho lỗi DB tạm thời, backoff luỹ thừa.
 * Hiện thực {@code app/workers/retry.py}: lỗi integrity/data/nghiệp vụ thất bại ngay, KHÔNG retry.
 *
 * <p>Lưu ý: executor KHÔNG giữ transaction trong lúc ngủ backoff; mỗi lần gọi {@code operation} nên tự mở transaction mới.
 */
@Component
public class WorkerRetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(WorkerRetryExecutor.class);

    public static final int DEFAULT_MAX_RETRIES = 3;
    public static final long DEFAULT_BACKOFF_MILLIS = 500L;

    /** SQLSTATE tạm thời (Python: TRANSIENT_SQLSTATES). */
    public static final Set<String> TRANSIENT_SQLSTATES = Set.of(
            "40001", // serialization_failure
            "40P01", // deadlock_detected
            "57P01", // admin_shutdown
            "57P02", // crash_shutdown
            "57P03", // cannot_connect_now
            "08000", // connection_exception
            "08003", // connection_does_not_exist
            "08006"  // connection_failure
    );

    /** Cho phép test thay thế Thread.sleep. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final Sleeper sleeper;

    public WorkerRetryExecutor() {
        this(Thread::sleep);
    }

    WorkerRetryExecutor(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    public <T> T executeWithRetry(Callable<T> operation, String operationName) throws Exception {
        return executeWithRetry(operation, DEFAULT_MAX_RETRIES, DEFAULT_BACKOFF_MILLIS, operationName);
    }

    /**
     * @param maxRetries    số lần thử lại tối đa (tổng số lần gọi = maxRetries + 1)
     * @param backoffMillis độ trễ cơ sở; lần thử lại thứ n chờ {@code backoffMillis * 2^(n-1)}
     */
    public <T> T executeWithRetry(Callable<T> operation, int maxRetries, long backoffMillis, String operationName)
            throws Exception {
        int attempt = 0;
        while (true) {
            try {
                return operation.call();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                attempt++;
                boolean transientError = isTransientDbError(e);
                if (attempt > maxRetries || !transientError) {
                    log.error("[{}] Failed (attempt {}/{}, non-retryable or max retries exceeded): {}",
                            operationName, attempt, maxRetries + 1, e.toString());
                    throw e;
                }
                long delay = backoffMillis * (1L << (attempt - 1));
                log.warn("[{}] Transient DB error on attempt {}/{}: {}. Retrying in {} ms...",
                        operationName, attempt, maxRetries + 1, e.toString(), delay);
                if (delay > 0) {
                    try {
                        sleeper.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ie;
                    }
                }
            }
        }
    }

    /**
     * Lỗi DB tạm thời đáng retry? Không bao giờ coi integrity/data violation hay lỗi nghiệp vụ là tạm thời.
     * Duyệt cả chuỗi nguyên nhân (cause chain).
     */
    public static boolean isTransientDbError(Throwable error) {
        boolean transientFound = false;
        Throwable current = error;
        for (int depth = 0; current != null && depth < 12; depth++, current = current.getCause()) {
            if (isIntegrityOrDataError(current)) {
                return false;
            }
            if (isTransientSingle(current)) {
                transientFound = true;
            }
        }
        return transientFound;
    }

    private static boolean isIntegrityOrDataError(Throwable t) {
        if (t instanceof DataIntegrityViolationException
                || t instanceof java.sql.SQLIntegrityConstraintViolationException
                || t instanceof org.hibernate.exception.ConstraintViolationException
                || t instanceof org.hibernate.exception.DataException) {
            return true;
        }
        if (t instanceof SQLException sql && sql.getSQLState() != null) {
            String cls = sql.getSQLState().length() >= 2 ? sql.getSQLState().substring(0, 2) : "";
            return cls.equals("22") || cls.equals("23");
        }
        return false;
    }

    private static boolean isTransientSingle(Throwable t) {
        if (t instanceof SQLException sql) {
            String state = sql.getSQLState();
            if (state != null && (TRANSIENT_SQLSTATES.contains(state) || state.startsWith("08"))) {
                return true;
            }
        }
        return t instanceof SQLTransientException
                || t instanceof SQLRecoverableException
                || t instanceof TransientDataAccessException
                || t instanceof DataAccessResourceFailureException
                || t instanceof CannotCreateTransactionException
                || t instanceof JDBCConnectionException
                || t instanceof LockAcquisitionException;
    }
}
