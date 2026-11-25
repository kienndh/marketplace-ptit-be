package com.ptiters.marketplace.configuration;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Startup fail-closed safety validator active ONLY under the 'e2e' profile.
 * Guarantees the application will never connect to populated development (marketplace_java_dev)
 * or legacy databases when running interactive local E2E tests.
 */
@Slf4j
@Component
@Profile("e2e")
@RequiredArgsConstructor
public class E2eDatabaseSafetyValidator {

    private final DataSource dataSource;

    @PostConstruct
    public void validateE2eDatabaseIsolation() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT current_database()")) {
            if (rs.next()) {
                String dbName = rs.getString(1);
                if (!"marketplace_java_e2e".equals(dbName)) {
                    throw new IllegalStateException(
                            "[CRITICAL SAFETY VIOLATION] Profile 'e2e' is active, but the connected database is '" +
                            dbName + "'. Expected 'marketplace_java_e2e'. Startup aborted immediately to protect populated development and production databases!");
                }
                log.info("[E2E SAFETY] Confirmed connection to isolated E2E database '{}'. Safe to proceed.", dbName);
            }
        }
    }
}
