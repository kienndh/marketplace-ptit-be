package com.ptiters.marketplace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Khởi động context với profile MẶC ĐỊNH (trỏ marketplace_java_dev) chỉ để Hibernate validate schema.
 * READ-ONLY: chỉ chạy SELECT, tuyệt đối không ghi.
 */
@SpringBootTest
class DevSchemaReadOnlyValidationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Environment environment;

    @Test
    @DisplayName("Dev DB: toàn bộ @Entity khớp schema (ddl-auto=validate), chỉ SELECT")
    void entitiesValidateAgainstDevSchema() {
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("marketplace_java_dev", jdbc.queryForObject("SELECT current_database()", String.class));
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class) >= 0);
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM categories", Integer.class) >= 0);
    }
}
