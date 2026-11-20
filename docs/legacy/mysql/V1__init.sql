CREATE TABLE categories (
                            category_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                            name        VARCHAR(100) NOT NULL,
                            slug        VARCHAR(120) NOT NULL,
                            is_deleted  BOOLEAN NOT NULL DEFAULT FALSE,
                            created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            UNIQUE KEY ux_categories_slug (slug)
);

CREATE TABLE users (
                       user_id             BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                       email               VARCHAR(150) NOT NULL,
                       full_name           VARCHAR(100) NOT NULL,
                       phone               VARCHAR(20) NULL,
                       avatar_url          VARCHAR(500) NULL,
                       bio                 VARCHAR(500) NULL,
                       delivery_area       VARCHAR(255) NULL,
                       default_meetup_area VARCHAR(255) NULL,
                       bank_bin            VARCHAR(10) NULL,
                       bank_account_number VARCHAR(30) NULL,
                       bank_account_name   VARCHAR(100) NULL,
                       role                VARCHAR(10) NOT NULL DEFAULT 'STUDENT',
                       status              VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
                       last_active_at      DATETIME NULL,
                       created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                           ON UPDATE CURRENT_TIMESTAMP,
                       UNIQUE KEY ux_users_email (email),
                       CONSTRAINT chk_users_role CHECK (role IN ('STUDENT', 'ADMIN')),
                       CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'BANNED', 'INACTIVE'))
);

CREATE TABLE user_auth_identities (
                                      user_auth_identity_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                      user_id      BIGINT NOT NULL,
                                      provider     VARCHAR(30) NOT NULL DEFAULT 'FIREBASE',
                                      provider_uid VARCHAR(128) NOT NULL,
                                      created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                          ON UPDATE CURRENT_TIMESTAMP,
                                      UNIQUE KEY ux_identities_user (user_id),
                                      UNIQUE KEY ux_identities_provider_uid (provider, provider_uid),
                                      CONSTRAINT fk_identities_users FOREIGN KEY (user_id) REFERENCES users (user_id),
                                      CONSTRAINT chk_identities_provider CHECK (provider IN ('FIREBASE'))
);