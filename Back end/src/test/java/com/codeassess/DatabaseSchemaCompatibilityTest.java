package com.codeassess;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSchemaCompatibilityTest {

    @Test
    void v6_should_be_a_safe_static_compatibility_contract_for_rule_score_and_evidence() throws IOException {
        Path v6 = Path.of("src/main/resources/db/migration/V6__align_scoring_and_evidence_columns.sql");
        String sql = Files.readString(v6);

        List<String> requiredChecks = List.of(
                "ADD COLUMN score DECIMAL(5,2) NULL",
                "UPDATE rule_scores",
                "score IS NULL",
                "score_awarded IS NOT NULL",
                "ADD COLUMN submission_a_start_line INT NULL",
                "ADD COLUMN submission_a_end_line INT NULL",
                "ADD COLUMN submission_b_start_line INT NULL",
                "ADD COLUMN submission_b_end_line INT NULL",
                "does not drop legacy columns",
                "Do not rewrite V6",
                "not proven"
        );

        for (String expected : requiredChecks) {
            assertTrue(sql.contains(expected),
                    "Expected migration to include safe compatibility logic for: " + expected);
        }

        assertFalse(sql.lines().anyMatch(line ->
                        line.trim().toUpperCase().startsWith("ADD COLUMN IF NOT EXISTS")
                                || line.trim().toUpperCase().contains("ADD COLUMN IF NOT EXISTS")),
                "MySQL Flyway migrations must not use MariaDB-only IF NOT EXISTS column guards");
        assertFalse(sql.contains("DROP COLUMN"),
                "V6 must remain non-destructive");
        assertFalse(sql.contains("DROP TABLE"),
                "V6 must remain non-destructive");
    }
}
