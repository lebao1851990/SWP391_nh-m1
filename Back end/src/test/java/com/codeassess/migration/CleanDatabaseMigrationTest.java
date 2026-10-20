package com.codeassess.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class CleanDatabaseMigrationTest {

    private static final String TEST_DB = "plagiarism_system_clean_test";
    private static final String ROOT_URL = "jdbc:mysql://localhost:3306/?useSSL=false&allowPublicKeyRetrieval=true";
    private static final String TEST_DB_URL = "jdbc:mysql://localhost:3306/" + TEST_DB + "?useSSL=false&allowPublicKeyRetrieval=true";
    private static final String PROD_DB_URL = "jdbc:mysql://localhost:3306/plagiarism_system?useSSL=false&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "123456";

    @Test
    @DisplayName("Verify Clean Database Migration and Hibernate Validation from Scratch")
    void testCleanDatabaseMigrationAndHibernateValidation() throws Exception {
        // 1. Snapshot original database table counts
        Map<String, Integer> originalCountsBefore = getTableCounts(PROD_DB_URL);
        assertFalse(originalCountsBefore.isEmpty(), "Original database should have tables");
        System.out.println("Original DB table count: " + originalCountsBefore.size());

        try {
            // 2. Create clean empty test database
            try (Connection conn = DriverManager.getConnection(ROOT_URL, DB_USER, DB_PASS);
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DROP DATABASE IF EXISTS " + TEST_DB);
                stmt.execute("CREATE DATABASE " + TEST_DB + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }

            // 3. Execute Flyway migration on clean database
            Flyway flyway = Flyway.configure()
                    .dataSource(TEST_DB_URL, DB_USER, DB_PASS)
                    .locations("classpath:db/migration")
                    .load();

            MigrateResult result = flyway.migrate();
            System.out.println("Flyway migrations executed: " + result.migrationsExecuted);
            System.out.println("Flyway target schema version: " + result.targetSchemaVersion);

            assertTrue(result.success, "Flyway migrations must succeed");
            assertEquals(13, result.migrationsExecuted, "Must execute exactly 13 migrations (V1, V1.1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12)");
            assertEquals("12", result.targetSchemaVersion, "Target version must be 12");

            // 4. Verify all core tables exist in clean DB
            Set<String> testTables = getTableNames(TEST_DB_URL);
            String[] expectedTables = {
                    "ast_fingerprints", "class_students", "classes", "exam_classes", "exam_matrix_coordination", "exams",
                    "grade_audit_logs", "plagiarism_appeals", "plagiarism_evidence", "rule_scores",
                    "scoring_criteria", "similarity_matrix", "submissions", "users"
            };

            for (String expected : expectedTables) {
                assertTrue(testTables.contains(expected), "Missing table in clean DB: " + expected);
            }
            assertTrue(testTables.contains("flyway_schema_history"), "flyway_schema_history must exist");

            // 5. Verify Foreign Keys
            List<String> foreignKeys = getForeignKeys(TEST_DB_URL);
            System.out.println("Clean DB Foreign Key count: " + foreignKeys.size());
            assertTrue(foreignKeys.size() >= 14, "Must have at least 14 foreign key constraints");

            // 6. Verify Hibernate Validation against Clean Migrated Database
            System.out.println("Bootstrapping Hibernate SchemaValidator against " + TEST_DB + "...");
            Map<String, Object> settings = new HashMap<>();
            settings.put("hibernate.connection.url", TEST_DB_URL);
            settings.put("hibernate.connection.username", DB_USER);
            settings.put("hibernate.connection.password", DB_PASS);
            settings.put("hibernate.connection.driver_class", "com.mysql.cj.jdbc.Driver");
            settings.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
            settings.put("hibernate.hbm2ddl.auto", "validate");

            StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                    .applySettings(settings)
                    .build();

            MetadataSources sources = new MetadataSources(registry);
            sources.addAnnotatedClass(com.codeassess.user.entity.User.class);
            sources.addAnnotatedClass(com.codeassess.classes.entity.CourseClass.class);
            sources.addAnnotatedClass(com.codeassess.classes.entity.ClassStudent.class);
            sources.addAnnotatedClass(com.codeassess.exam.entity.Exam.class);
            sources.addAnnotatedClass(com.codeassess.submission.entity.Submission.class);
            sources.addAnnotatedClass(com.codeassess.plagiarism.entity.AstFingerprint.class);
            sources.addAnnotatedClass(com.codeassess.plagiarism.entity.SimilarityPair.class);
            sources.addAnnotatedClass(com.codeassess.plagiarism.entity.PlagiarismEvidence.class);
            sources.addAnnotatedClass(com.codeassess.plagiarism.entity.Appeal.class);
            sources.addAnnotatedClass(com.codeassess.scoring.entity.ScoringCriteria.class);
            sources.addAnnotatedClass(com.codeassess.scoring.entity.RuleScore.class);
            sources.addAnnotatedClass(com.codeassess.scoring.entity.GradeAuditLog.class);
            sources.addAnnotatedClass(com.codeassess.plagiarism.entity.ExamMatrixCoordination.class);

            try {
                // Building the session factory forces Hibernate validation of all 13 entities against clean DB
                var sessionFactory = sources.buildMetadata().buildSessionFactory();
                sessionFactory.close();
                System.out.println("Hibernate validation PASSED for all 13 entities with ZERO schema errors!");
            } catch (SchemaManagementException ex) {
                fail("Hibernate validation failed: " + ex.getMessage());
            } finally {
                StandardServiceRegistryBuilder.destroy(registry);
            }

            // 7. Verify original database is 100% untouched
            Map<String, Integer> originalCountsAfter = getTableCounts(PROD_DB_URL);
            assertEquals(originalCountsBefore, originalCountsAfter, "Original database must be completely untouched");
            System.out.println("Original database state verified: 100% untouched!");

        } finally {
            // 8. Cleanup test database
            try (Connection conn = DriverManager.getConnection(ROOT_URL, DB_USER, DB_PASS);
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DROP DATABASE IF EXISTS " + TEST_DB);
            }
        }
    }

    private Map<String, Integer> getTableCounts(String url) throws Exception {
        Map<String, Integer> map = new TreeMap<>();
        try (Connection conn = DriverManager.getConnection(url, DB_USER, DB_PASS)) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            try (ResultSet rs = meta.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String table = rs.getString("TABLE_NAME");
                    try (Statement stmt = conn.createStatement();
                         ResultSet countRs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
                        if (countRs.next()) {
                            map.put(table, countRs.getInt(1));
                        }
                    }
                }
            }
        }
        return map;
    }

    private Set<String> getTableNames(String url) throws Exception {
        Set<String> set = new TreeSet<>();
        try (Connection conn = DriverManager.getConnection(url, DB_USER, DB_PASS)) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            try (ResultSet rs = meta.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    set.add(rs.getString("TABLE_NAME"));
                }
            }
        }
        return set;
    }

    private List<String> getForeignKeys(String url) throws Exception {
        List<String> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url, DB_USER, DB_PASS);
             ResultSet rs = conn.createStatement().executeQuery(
                     "SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME " +
                     "FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE " +
                     "WHERE TABLE_SCHEMA = '" + TEST_DB + "' AND REFERENCED_TABLE_NAME IS NOT NULL")) {
            while (rs.next()) {
                list.add(rs.getString("TABLE_NAME") + "." + rs.getString("COLUMN_NAME") + " -> " +
                        rs.getString("REFERENCED_TABLE_NAME") + "." + rs.getString("REFERENCED_COLUMN_NAME"));
            }
        }
        return list;
    }
}
