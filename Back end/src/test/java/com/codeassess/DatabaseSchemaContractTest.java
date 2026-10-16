package com.codeassess;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source-level Flyway contract for Row 11–12.
 * This does not inspect live MySQL and must not be treated as live schema verification.
 */
class DatabaseSchemaContractTest {

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");

    @Test
    void flywayChainShouldContainExactlyOneV1AndExpectedVersions() throws IOException {
        List<String> names = migrationFileNames();

        List<String> v1Files = names.stream()
                .filter(name -> name.startsWith("V1__"))
                .collect(Collectors.toList());
        assertThat(v1Files)
                .as("duplicate V1 files would make Flyway fail")
                .containsExactly("V1__initial_schema.sql");

        assertThat(names).contains(
                "V1__initial_schema.sql",
                "V1.1__extend_users_schema.sql",
                "V2__create_classes_and_extend_exams.sql",
                "V3__extend_submissions_schema.sql",
                "V4__extend_appeals_schema.sql",
                "V5__create_scoring_and_plagiarism_evidence.sql"
        );
    }

    @Test
    void sourceMigrationsShouldDeclareCoreTablesPrimaryKeysAndForeignKeys() throws IOException {
        String sql = concatenatedMigrations(
                "V1__initial_schema.sql",
                "V1.1__extend_users_schema.sql",
                "V2__create_classes_and_extend_exams.sql",
                "V3__extend_submissions_schema.sql",
                "V4__extend_appeals_schema.sql",
                "V5__create_scoring_and_plagiarism_evidence.sql"
        );

        assertThat(createTableNames(sql)).contains(
                "users",
                "exams",
                "submissions",
                "ast_fingerprints",
                "similarity_matrix",
                "plagiarism_appeals",
                "classes",
                "class_students",
                "scoring_criteria",
                "rule_scores",
                "plagiarism_evidence"
        );

        assertThat(sql).contains(
                "CONSTRAINT fk_submissions_exam",
                "CONSTRAINT fk_submissions_student",
                "CONSTRAINT fk_classes_teacher",
                "CONSTRAINT fk_class_students_class",
                "CONSTRAINT fk_class_students_student",
                "CONSTRAINT fk_exams_class",
                "CONSTRAINT fk_scoring_criteria_exam",
                "CONSTRAINT fk_rule_scores_submission",
                "CONSTRAINT fk_rule_scores_criteria",
                "CONSTRAINT fk_evidence_similarity"
        );

        assertThat(sql).contains("PRIMARY KEY (class_id, student_id)");
        assertThat(sql).contains("CHECK (role IN ('ADMIN', 'TEACHER', 'STUDENT'))");
        assertThat(sql).contains("UNIQUE KEY uk_similarity_submission_pair");
    }

    private static List<String> migrationFileNames() throws IOException {
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(MIGRATION_DIR, "*.sql")) {
            for (Path path : stream) {
                names.add(path.getFileName().toString());
            }
        }
        names.sort(String::compareTo);
        return names;
    }

    private static String concatenatedMigrations(String... files) throws IOException {
        StringBuilder sql = new StringBuilder();
        for (String file : files) {
            sql.append(Files.readString(MIGRATION_DIR.resolve(file))).append('\n');
        }
        return sql.toString();
    }

    private static List<String> createTableNames(String sql) {
        Matcher matcher = Pattern.compile("CREATE TABLE\\s+([a-zA-Z0-9_]+)", Pattern.CASE_INSENSITIVE)
                .matcher(sql);
        List<String> tables = new ArrayList<>();
        while (matcher.find()) {
            tables.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return tables;
    }
}
