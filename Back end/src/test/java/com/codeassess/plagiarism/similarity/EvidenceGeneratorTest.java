package com.codeassess.plagiarism.similarity;

import com.codeassess.plagiarism.entity.AstFingerprint;
import com.codeassess.plagiarism.entity.PlagiarismEvidence;
import com.codeassess.plagiarism.entity.SimilarityPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceGeneratorTest {

    private EvidenceGenerator generator;
    private SimilarityPair pair;

    @BeforeEach
    void setUp() {
        generator = new EvidenceGenerator();
        pair = SimilarityPair.builder().id(999L).build();
    }

    @Test
    @DisplayName("generateEvidence: Shared hashes across multiple source files produce correct relative paths and line ranges")
    void generateEvidence_multiFile_producesCorrectLocations() {
        AstFingerprint fpA1 = AstFingerprint.builder()
                .hashValue(111L)
                .nodeType("MethodDeclaration[void calculate()]")
                .startLine(10)
                .endLine(25)
                .filePath("src/com/example/Calculator.java")
                .build();

        AstFingerprint fpA2 = AstFingerprint.builder()
                .hashValue(222L)
                .nodeType("ClassOrInterfaceDeclaration[class Helper]")
                .startLine(1)
                .endLine(50)
                .filePath("src/com/example/Helper.java")
                .build();

        AstFingerprint fpB1 = AstFingerprint.builder()
                .hashValue(111L)
                .nodeType("MethodDeclaration[void compute()]")
                .startLine(15)
                .endLine(30)
                .filePath("com/billing/PaymentCalculator.java")
                .build();

        AstFingerprint fpB2 = AstFingerprint.builder()
                .hashValue(222L)
                .nodeType("ClassOrInterfaceDeclaration[class Util]")
                .startLine(5)
                .endLine(55)
                .filePath("com/billing/Util.java")
                .build();

        List<PlagiarismEvidence> evidences = generator.generateEvidence(pair, List.of(fpA1, fpA2), List.of(fpB1, fpB2));

        assertThat(evidences).hasSize(2);

        PlagiarismEvidence calcEvidence = evidences.stream()
                .filter(e -> "MethodDeclaration".equals(e.getEvidenceType()))
                .findFirst()
                .orElseThrow();

        assertThat(calcEvidence.getSimilarityPair()).isEqualTo(pair);
        assertThat(calcEvidence.getEvidenceType()).isEqualTo("MethodDeclaration");
        assertThat(calcEvidence.getLocationA()).isEqualTo("src/com/example/Calculator.java:10-25");
        assertThat(calcEvidence.getLocationB()).isEqualTo("com/billing/PaymentCalculator.java:15-30");
        assertThat(calcEvidence.getStartLineA()).isEqualTo(10);
        assertThat(calcEvidence.getEndLineA()).isEqualTo(25);
        assertThat(calcEvidence.getStartLineB()).isEqualTo(15);
        assertThat(calcEvidence.getEndLineB()).isEqualTo(30);
        assertThat(calcEvidence.getDescription()).contains("src/com/example/Calculator.java:10-25");

        PlagiarismEvidence helperEvidence = evidences.stream()
                .filter(e -> "ClassOrInterfaceDeclaration".equals(e.getEvidenceType()))
                .findFirst()
                .orElseThrow();

        assertThat(helperEvidence.getLocationA()).isEqualTo("src/com/example/Helper.java:1-50");
        assertThat(helperEvidence.getLocationB()).isEqualTo("com/billing/Util.java:5-55");
    }

    @Test
    @DisplayName("generateEvidence: Single-file fallback when file paths are null")
    void generateEvidence_nullFilePath_fallsBackToLineFormat() {
        AstFingerprint fpA = AstFingerprint.builder()
                .hashValue(333L)
                .nodeType("BlockStmt")
                .startLine(100)
                .endLine(120)
                .filePath(null)
                .build();

        AstFingerprint fpB = AstFingerprint.builder()
                .hashValue(333L)
                .nodeType("BlockStmt")
                .startLine(200)
                .endLine(220)
                .filePath(null)
                .build();

        List<PlagiarismEvidence> evidences = generator.generateEvidence(pair, List.of(fpA), List.of(fpB));

        assertThat(evidences).hasSize(1);
        PlagiarismEvidence evidence = evidences.get(0);
        assertThat(evidence.getLocationA()).isEqualTo("Lines 100–120");
        assertThat(evidence.getLocationB()).isEqualTo("Lines 200–220");
        assertThat(evidence.getDescription()).contains("Lines 100–120");
    }

    @Test
    @DisplayName("generateEvidence: Input list and map structures remain immutable")
    void generateEvidence_inputStructuresRemainImmutable() {
        AstFingerprint fpA1 = AstFingerprint.builder().hashValue(10L).nodeType("Node").startLine(1).endLine(2).build();
        AstFingerprint fpA2 = AstFingerprint.builder().hashValue(20L).nodeType("Node").startLine(3).endLine(4).build();
        AstFingerprint fpB = AstFingerprint.builder().hashValue(10L).nodeType("Node").startLine(5).endLine(6).build();

        List<AstFingerprint> listA = List.of(fpA1, fpA2);
        List<AstFingerprint> listB = List.of(fpB);

        List<PlagiarismEvidence> evidences = generator.generateEvidence(pair, listA, listB);

        assertThat(evidences).hasSize(1);
        // Original lists untouched
        assertThat(listA).containsExactly(fpA1, fpA2);
        assertThat(listB).containsExactly(fpB);
    }

    @Test
    @DisplayName("generateEvidence: Empty inputs produce empty evidence list")
    void generateEvidence_emptyInputs_returnsEmptyList() {
        List<PlagiarismEvidence> evidences1 = generator.generateEvidence(pair, Collections.emptyList(), Collections.emptyList());
        assertThat(evidences1).isEmpty();

        AstFingerprint fpA = AstFingerprint.builder().hashValue(1L).nodeType("Node").startLine(1).endLine(2).build();
        List<PlagiarismEvidence> evidences2 = generator.generateEvidence(pair, List.of(fpA), Collections.emptyList());
        assertThat(evidences2).isEmpty();
    }

    @Test
    @DisplayName("generateEvidence: Non-overlapping hash sets produce empty evidence list")
    void generateEvidence_nonOverlapping_returnsEmptyList() {
        AstFingerprint fpA = AstFingerprint.builder().hashValue(100L).nodeType("Node").startLine(1).endLine(2).build();
        AstFingerprint fpB = AstFingerprint.builder().hashValue(200L).nodeType("Node").startLine(1).endLine(2).build();

        List<PlagiarismEvidence> evidences = generator.generateEvidence(pair, List.of(fpA), List.of(fpB));
        assertThat(evidences).isEmpty();
    }
}
