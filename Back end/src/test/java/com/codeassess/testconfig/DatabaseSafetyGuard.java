package com.codeassess.testconfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Safety guard enforcing strict isolation between development/production
 * databases and automated test suites.
 *
 * Any test attempting to connect to 'plagiarism_system' will be immediately
 * halted with an IllegalStateException to prevent accidental data loss.
 */
public class DatabaseSafetyGuard {

    private static final String PROD_DEV_DATABASE = "plagiarism_system";

    /**
     * Asserts that the provided DataSource connects to a safe, designated test database.
     * Throws IllegalStateException if the database is 'plagiarism_system' or does not
     * conform to test naming conventions.
     *
     * @param dataSource the active DataSource to inspect
     */
    public static void assertSafeTestDatabase(DataSource dataSource) {
        if (dataSource == null) {
            return;
        }

        try (Connection conn = dataSource.getConnection()) {
            String catalog = conn.getCatalog();
            String rawUrl = conn.getMetaData().getURL();
            String sanitizedUrl = sanitizeUrl(rawUrl);

            System.out.println("==================================================================");
            System.out.println("[DATABASE-SAFETY-GUARD] Active test catalog: " + catalog);
            System.out.println("[DATABASE-SAFETY-GUARD] Active test JDBC URL: " + sanitizedUrl);
            System.out.println("==================================================================");

            if (catalog == null || catalog.trim().isEmpty()) {
                throw new IllegalStateException(
                        "DATABASE SAFETY VIOLATION: Cannot determine active database catalog from connection metadata! " +
                        "Execution aborted to prevent accidental operation on unverified databases."
                );
            }

            // CRITICAL CHECK: Refuse if pointing to dev/demo database
            if (PROD_DEV_DATABASE.equalsIgnoreCase(catalog) ||
                    (rawUrl != null && rawUrl.toLowerCase().contains("/" + PROD_DEV_DATABASE + "?"))) {
                throw new IllegalStateException(
                        "CRITICAL DATABASE SAFETY VIOLATION: Test suite execution was aborted because the active " +
                        "datasource is connected to the development/demo database '" + PROD_DEV_DATABASE + "'! " +
                        "Tests must strictly run against a dedicated test database (e.g., 'plagiarism_system_test')."
                );
            }

            // Ensure catalog follows test naming pattern
            if (!catalog.toLowerCase().endsWith("_test") && !catalog.toLowerCase().contains("test")) {
                throw new IllegalStateException(
                        "DATABASE SAFETY VIOLATION: Active database '" + catalog + "' does not contain 'test'. " +
                        "Tests are strictly restricted to dedicated test databases to avoid impacting live data."
                );
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "DATABASE SAFETY GUARD: Failed to inspect test datasource connection metadata: " + e.getMessage(), e
            );
        }
    }

    /**
     * Sanitizes JDBC URL to prevent printing passwords or sensitive query parameters in logs.
     */
    public static String sanitizeUrl(String url) {
        if (url == null) {
            return "null";
        }
        return url.replaceAll("(?i)(password|pwd|user|username)=[^&;]*", "$1=***");
    }
}
