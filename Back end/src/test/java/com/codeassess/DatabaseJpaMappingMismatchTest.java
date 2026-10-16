package com.codeassess;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source-level proof of the two JPA vs V5 mismatches found in Row 11–12.
 * Live MySQL is not consulted.
 */
class DatabaseJpaMappingMismatchTest {

    private static final Path ROOT = Path.of("src");

    @Test
    void ruleScoreEntityMapsScoreWhileV5CreatesScoreAwarded() throws IOException {
        String v5 = Files.readString(ROOT.resolve("main/resources/db/migration/V5__create_scoring_and_plagiarism_evidence.sql"));
        String entity = Files.readString(ROOT.resolve("main/java/com/codeassess/scoring/entity/RuleScore.java"));

        int ruleScoresStart = v5.indexOf("CREATE TABLE rule_scores");
        int nextTable = v5.indexOf("CREATE TABLE", ruleScoresStart + 1);
        String ruleScoresSql = v5.substring(ruleScoresStart, nextTable);
        assertThat(ruleScoresSql).contains("score_awarded DECIMAL(5,2) NOT NULL");
        assertThat(ruleScoresSql).doesNotContain("score DECIMAL");

        assertThat(entity).contains("@Table(name = \"rule_scores\")");
        assertThat(entity).contains("@Column(name = \"score\"");
        assertThat(entity).doesNotContain("score_awarded");
    }

    @Test
    void plagiarismEvidenceEntityMapsLineColumnsWhileV5CreatesLocationStrings() throws IOException {
        String v5 = Files.readString(ROOT.resolve("main/resources/db/migration/V5__create_scoring_and_plagiarism_evidence.sql"));
        String entity = Files.readString(ROOT.resolve("main/java/com/codeassess/plagiarism/entity/PlagiarismEvidence.java"));

        assertThat(v5).contains("CREATE TABLE plagiarism_evidence");
        assertThat(v5).contains("submission_a_location VARCHAR(100)");
        assertThat(v5).contains("submission_b_location VARCHAR(100)");
        assertThat(v5).doesNotContain("submission_a_start_line");

        assertThat(entity).contains("@Table(name = \"plagiarism_evidence\")");
        assertThat(entity).contains("@Column(name = \"submission_a_start_line\")");
        assertThat(entity).contains("@Column(name = \"submission_a_end_line\")");
        assertThat(entity).contains("@Column(name = \"submission_b_start_line\")");
        assertThat(entity).contains("@Column(name = \"submission_b_end_line\")");
        assertThat(entity).doesNotContain("submission_a_location");
        assertThat(entity).doesNotContain("submission_b_location");
    }
}
