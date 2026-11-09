
CREATE TABLE cloudinary_cleanup_tasks (
                                          task_id BIGINT NOT NULL AUTO_INCREMENT,
                                          public_id VARCHAR(255) NOT NULL,

                                          status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                          retry_count INT NOT NULL DEFAULT 0,
                                          max_retries INT NOT NULL DEFAULT 5,

                                          next_retry_at DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6),

                                          last_error TEXT NULL,

                                          created_at DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6),

                                          updated_at DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

                                          PRIMARY KEY (task_id),

                                          UNIQUE KEY uk_cleanup_public_id (public_id),

                                          INDEX idx_cleanup_status_retry (
        status,
        next_retry_at
    )
) ENGINE = InnoDB;
