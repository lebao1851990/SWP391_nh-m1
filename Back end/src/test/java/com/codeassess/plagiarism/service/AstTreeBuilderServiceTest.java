package com.codeassess.plagiarism.service;

import com.codeassess.plagiarism.dto.AstTreeComparisonDto;
import com.codeassess.plagiarism.dto.AstTreeNodeDto;
import com.codeassess.plagiarism.entity.PlagiarismEvidence;
import com.codeassess.plagiarism.entity.SimilarityPair;
import com.codeassess.submission.entity.Submission;
import com.codeassess.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AstTreeBuilderServiceTest {

    private AstTreeBuilderService service;
    private SimilarityPair pair;
    private Submission subA;
    private Submission subB;

    private static final String CODE_A = """
            public class SolutionA {
                public static int calculateSum(int limit) {
                    int sum = 0;
                    for (int i = 0; i <= limit; i++) {
                        if (isPrime(i)) {
                            sum += i;
                        }
                    }
                    return sum;
                }
                private static boolean isPrime(int n) {
                    return n > 1;
                }
            }
            """;

    private static final String CODE_B = """
            public class SolutionB {
                public static int computeTotal(int max) {
                    int total = 0;
                    for (int num = 0; num <= max; num++) {
                        if (checkPrime(num)) {
                            total += num;
                        }
                    }
                    return total;
                }
                private static boolean checkPrime(int x) {
                    return x > 1;
                }
            }
            """;

    @BeforeEach
    void setUp() {
        service = new AstTreeBuilderService();

        User studentA = User.builder().id("std-1").fullName("Sinh viên A").build();
        User studentB = User.builder().id("std-2").fullName("Sinh viên B").build();

        subA = Submission.builder().id("sub-a-uuid").student(studentA).sourceCode(CODE_A).build();
        subB = Submission.builder().id("sub-b-uuid").student(studentB).sourceCode(CODE_B).build();

        pair = SimilarityPair.builder()
                .id(1001L)
                .submissionA(subA)
                .submissionB(subB)
                .build();
    }

    @Test
    @DisplayName("1. Build Tree: Real Java code builds accurate parent-child hierarchy and line numbers")
    void testBuildTree_RealJavaCode_ParentChild() {
        AstTreeComparisonDto result = service.buildComparisonTree(pair, List.of());

        assertThat(result).isNotNull();
        assertThat(result.getParseStatus()).isEqualTo("SUCCESS");
        assertThat(result.getTreeA()).isNotNull();
        assertThat(result.getTreeB()).isNotNull();

        // Root is CompilationUnit
        AstTreeNodeDto rootA = result.getTreeA();
        assertThat(rootA.getNodeType()).isEqualTo("CompilationUnit");

        // First child is ClassOrInterfaceDeclaration
        assertThat(rootA.getChildren()).isNotEmpty();
        AstTreeNodeDto classNode = rootA.getChildren().get(0);
        assertThat(classNode.getNodeType()).isEqualTo("ClassOrInterfaceDeclaration");
        assertThat(classNode.getLabel()).contains("class SolutionA");

        // Children of class include MethodDeclarations
        List<AstTreeNodeDto> classMembers = classNode.getChildren();
        assertThat(classMembers).anyMatch(n -> n.getNodeType().equals("MethodDeclaration") && n.getLabel().contains("calculateSum"));
        assertThat(classMembers).anyMatch(n -> n.getNodeType().equals("MethodDeclaration") && n.getLabel().contains("isPrime"));

        // Inside calculateSum, find BlockStmt -> ForStmt
        AstTreeNodeDto calcMethod = classMembers.stream()
                .filter(n -> n.getLabel().contains("calculateSum"))
                .findFirst().orElseThrow();

        assertThat(calcMethod.getChildren()).isNotEmpty();
        AstTreeNodeDto methodBody = calcMethod.getChildren().stream()
                .filter(n -> n.getNodeType().equals("BlockStmt"))
                .findFirst().orElseThrow();
        assertThat(methodBody.getNodeType()).isEqualTo("BlockStmt");

        assertThat(methodBody.getChildren()).anyMatch(n -> n.getNodeType().equals("ForStmt"));
    }

    @Test
    @DisplayName("2. Evidence Mapping: Nodes inside span are MATCHED_REGION with null matchedNodeId")
    void testEvidenceMapping_MatchedRegionOnly() {
        PlagiarismEvidence evidence = PlagiarismEvidence.builder()
                .id("ev-uuid-001")
                .startLineA(4)
                .endLineA(8)
                .startLineB(4)
                .endLineB(8)
                .evidenceType("ForStmt")
                .build();

        AstTreeComparisonDto result = service.buildComparisonTree(pair, List.of(evidence));

        assertThat(result.getParseStatus()).isEqualTo("SUCCESS");

        // Navigate to ForStmt on Tree A (around line 4-8)
        AstTreeNodeDto classNode = result.getTreeA().getChildren().get(0);
        AstTreeNodeDto methodNode = classNode.getChildren().stream()
                .filter(n -> n.getNodeType().equals("MethodDeclaration") && n.getLabel().contains("calculateSum"))
                .findFirst().orElseThrow();
        AstTreeNodeDto blockNode = methodNode.getChildren().stream()
                .filter(n -> n.getNodeType().equals("BlockStmt"))
                .findFirst().orElseThrow();

        AstTreeNodeDto forNode = blockNode.getChildren().stream()
                .filter(n -> n.getNodeType().equals("ForStmt"))
                .findFirst().orElseThrow();

        assertThat(forNode.getStartLine()).isEqualTo(4);
        assertThat(forNode.getEndLine()).isEqualTo(8);
        assertThat(forNode.getMatchStatus()).isEqualTo("MATCHED_REGION");
        assertThat(forNode.getEvidenceId()).isEqualTo("ev-uuid-001");
        assertThat(forNode.getMatchedNodeId()).isNull(); // Truthful: always null
    }

    @Test
    @DisplayName("3. Context Classification: Ancestors enclosing evidence span are CONTEXT, never MATCHED")
    void testContextNode_AncestorNeverMatched() {
        PlagiarismEvidence evidence = PlagiarismEvidence.builder()
                .id("ev-uuid-002")
                .startLineA(4)
                .endLineA(8)
                .startLineB(4)
                .endLineB(8)
                .evidenceType("ForStmt")
                .build();

        AstTreeComparisonDto result = service.buildComparisonTree(pair, List.of(evidence));

        AstTreeNodeDto rootA = result.getTreeA();
        AstTreeNodeDto classNode = rootA.getChildren().get(0);
        AstTreeNodeDto methodNode = classNode.getChildren().get(0);

        // Root, Class, and Method enclose lines 4-8 -> CONTEXT
        assertThat(rootA.getMatchStatus()).isEqualTo("CONTEXT");
        assertThat(classNode.getMatchStatus()).isEqualTo("CONTEXT");
        assertThat(methodNode.getMatchStatus()).isEqualTo("CONTEXT");

        assertThat(rootA.getEvidenceId()).isNull();
        assertThat(classNode.getEvidenceId()).isNull();
        assertThat(methodNode.getEvidenceId()).isNull();

        // Outside method isPrime (lines 12-14) -> UNMATCHED
        AstTreeNodeDto isPrimeMethod = classNode.getChildren().get(1);
        assertThat(isPrimeMethod.getMatchStatus()).isEqualTo("UNMATCHED");
    }

    @Test
    @DisplayName("4. Overlapping Evidence: Node inside multiple spans associates with narrowest span")
    void testOverlappingEvidence_PicksNarrowest() {
        PlagiarismEvidence wide = PlagiarismEvidence.builder()
                .id("ev-wide")
                .startLineA(2)
                .endLineA(11)
                .startLineB(2)
                .endLineB(11)
                .evidenceType("MethodDeclaration")
                .build();

        PlagiarismEvidence narrow = PlagiarismEvidence.builder()
                .id("ev-narrow")
                .startLineA(4)
                .endLineA(8)
                .startLineB(4)
                .endLineB(8)
                .evidenceType("ForStmt")
                .build();

        AstTreeComparisonDto result = service.buildComparisonTree(pair, List.of(wide, narrow));

        AstTreeNodeDto classNode = result.getTreeA().getChildren().get(0);
        AstTreeNodeDto calcMethod = classNode.getChildren().stream()
                .filter(n -> n.getNodeType().equals("MethodDeclaration") && n.getLabel().contains("calculateSum"))
                .findFirst().orElseThrow();
        AstTreeNodeDto blockNode = calcMethod.getChildren().stream()
                .filter(n -> n.getNodeType().equals("BlockStmt"))
                .findFirst().orElseThrow();

        AstTreeNodeDto forNode = blockNode.getChildren().stream()
                .filter(n -> n.getNodeType().equals("ForStmt"))
                .findFirst().orElseThrow();

        // forNode is inside both ev-wide (2..11) and ev-narrow (5..9). It must associate with ev-narrow
        assertThat(forNode.getMatchStatus()).isEqualTo("MATCHED_REGION");
        assertThat(forNode.getEvidenceId()).isEqualTo("ev-narrow");
    }

    @Test
    @DisplayName("5. Syntax Error: Unparseable Java code returns safe SYNTAX_ERROR status without crashing")
    void testSyntaxError_GracefulStatus() {
        subA.setSourceCode("public class Broken { int a = ; "); // Syntax error
        AstTreeComparisonDto result = service.buildComparisonTree(pair, List.of());

        assertThat(result.getParseStatus()).isEqualTo("SYNTAX_ERROR_A");
        assertThat(result.getTreeA()).isNull();
        assertThat(result.getParseErrorMessage()).contains("Bài A:");
    }

    @Test
    @DisplayName("6. Empty Source: Null or empty source code returns EMPTY_SOURCE status")
    void testEmptySource_HandledSafely() {
        subB.setSourceCode("   ");
        AstTreeComparisonDto result = service.buildComparisonTree(pair, List.of());

        assertThat(result.getParseStatus()).isEqualTo("EMPTY_SOURCE");
        assertThat(result.getTreeB()).isNull();
    }

    @Test
    @DisplayName("7. Input Too Large: Source code exceeding max size or lines returns FILE_TOO_LARGE")
    void testInputTooLarge_FileTooLarge() {
        StringBuilder sb = new StringBuilder("public class Big {\n");
        for (int i = 0; i < 5005; i++) {
            sb.append("    int v").append(i).append(" = ").append(i).append(";\n");
        }
        sb.append("}\n");

        subA.setSourceCode(sb.toString());
        AstTreeComparisonDto result = service.buildComparisonTree(pair, List.of());

        assertThat(result.getParseStatus()).isEqualTo("FILE_TOO_LARGE");
        assertThat(result.getTreeA()).isNull();
    }

    @Test
    @DisplayName("8. Null Pair Check: IllegalArgumentException thrown if SimilarityPair is null")
    void testNullPair_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> service.buildComparisonTree(null, List.of()));
    }
}
