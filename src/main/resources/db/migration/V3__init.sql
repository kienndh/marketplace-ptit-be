CREATE TABLE products (
                          product_id        BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                          seller_id         BIGINT NOT NULL,
                          category_id       INT NOT NULL,
                          title             VARCHAR(255) NOT NULL,
                          description       TEXT NULL,
                          listing_type      VARCHAR(12) NOT NULL DEFAULT 'FIXED_PRICE',
                          price             DECIMAL(12,0) NOT NULL,
                          stock             INT NOT NULL DEFAULT 1,
                          reserved_stock    INT NOT NULL DEFAULT 0,
                          status            VARCHAR(10) NOT NULL,
                          moderation_status VARCHAR(20) NOT NULL DEFAULT 'CLEAR',
                          view_count        INT NOT NULL DEFAULT 0,
                          is_deleted        BOOLEAN NOT NULL DEFAULT FALSE,
                          created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                              ON UPDATE CURRENT_TIMESTAMP,
                          CONSTRAINT fk_products_seller FOREIGN KEY (seller_id)
                              REFERENCES users (user_id) ON DELETE RESTRICT,
                          CONSTRAINT fk_products_category FOREIGN KEY (category_id)
                              REFERENCES categories (category_id),
                          CONSTRAINT chk_products_price CHECK (price >= 0),
                          CONSTRAINT chk_products_stock CHECK (stock >= 0),
                          CONSTRAINT chk_products_reserved CHECK (reserved_stock >= 0 AND reserved_stock <= stock),
                          CONSTRAINT chk_products_listing_type CHECK (listing_type IN ('FIXED_PRICE', 'AUCTION')),
                          CONSTRAINT chk_products_status CHECK (status IN ('AVAILABLE', 'HIDDEN', 'SOLD_OUT')),
                          CONSTRAINT chk_products_moderation CHECK (moderation_status IN ('CLEAR', 'BLOCKED')),
                          KEY ix_products_category_status (category_id, status, is_deleted, created_at),
                          KEY ix_products_seller_status (seller_id, status, created_at),
                          KEY ix_products_listing_status (listing_type, status, is_deleted, created_at)
);

CREATE TABLE product_images (
                                image_id     BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                product_id   BIGINT NOT NULL,
                                image_url    VARCHAR(500) NOT NULL,
                                sort_order   SMALLINT NOT NULL DEFAULT 0,
                                is_thumbnail BOOLEAN NOT NULL DEFAULT FALSE,
                                CONSTRAINT fk_product_images_product FOREIGN KEY (product_id)
                                    REFERENCES products (product_id) ON DELETE CASCADE,
                                CONSTRAINT ux_product_images_product_url UNIQUE (product_id, image_url)
);