package com.codeassess.plagiarism.normalizer;

import com.codeassess.plagiarism.entity.AstFingerprint;
import com.codeassess.plagiarism.entity.PlagiarismEvidence;
import com.codeassess.plagiarism.entity.SimilarityPair;
import com.codeassess.plagiarism.parser.AstNode;
import com.codeassess.plagiarism.parser.JavaAstParser;
import com.codeassess.plagiarism.similarity.EvidenceGenerator;
import com.codeassess.plagiarism.similarity.JaccardSimilarityCalculator;
import com.codeassess.plagiarism.similarity.SimilarityResult;
import com.codeassess.plagiarism.tokenizer.KGram;
import com.codeassess.plagiarism.tokenizer.KGramGenerator;
import com.codeassess.plagiarism.winnowing.FingerprintHasher;
import com.codeassess.plagiarism.winnowing.HashedKGram;
import com.codeassess.plagiarism.winnowing.Winnower;
import com.codeassess.submission.entity.Submission;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class AdversarialAstTest {

    private JavaAstParser parser;
    private AstNormalizer normalizer;
    private KGramGenerator kGramGen;
    private FingerprintHasher hasher;
    private Winnower winnower;
    private JaccardSimilarityCalculator calculator;
    private EvidenceGenerator evidenceGenerator;

    @BeforeEach
    void setUp() {
        parser = new JavaAstParser();
        normalizer = new AstNormalizer();
        kGramGen = new KGramGenerator(5);
        hasher = new FingerprintHasher();
        winnower = new Winnower(4);
        calculator = new JaccardSimilarityCalculator();
        evidenceGenerator = new EvidenceGenerator();
    }

    static class PipelineOutput {
        List<AstNode> nodes;
        List<KGram> kgrams;
        List<HashedKGram> winnowed;
        List<AstFingerprint> fingerprints;
        Set<Long> uniqueHashes;
    }

    private PipelineOutput runPipeline(String code, String submissionId) {
        PipelineOutput out = new PipelineOutput();
        CompilationUnit cu = parser.parse(code);
        out.nodes = normalizer.normalize(cu);
        out.kgrams = kGramGen.generateKGrams(out.nodes);

        List<HashedKGram> hashed = new ArrayList<>();
        for (int i = 0; i < out.kgrams.size(); i++) {
            KGram kg = out.kgrams.get(i);
            long hashValue = hasher.hash(kg.getConcatenatedTypeString());
            hashed.add(new HashedKGram(hashValue, i, kg.getRepresentativeNodeType(), kg.getStartLine(), kg.getEndLine()));
        }

        out.winnowed = winnower.winnow(hashed);
        out.uniqueHashes = out.winnowed.stream().map(HashedKGram::getHash).collect(Collectors.toSet());

        Submission sub = Submission.builder().id(submissionId).build();
        out.fingerprints = out.winnowed.stream().map(hk -> AstFingerprint.builder()
                .submission(sub)
                .hashValue(hk.getHash())
                .nodeType(hk.getNodeType())
                .startLine(hk.getStartLine())
                .endLine(hk.getEndLine())
                .build()).collect(Collectors.toList());

        return out;
    }

    private void printComparison(String caseName, String codeA, String codeB) {
        PipelineOutput outA = runPipeline(codeA, "sub-A");
        PipelineOutput outB = runPipeline(codeB, "sub-B");

        SimilarityResult result = calculator.calculate(outA.uniqueHashes, outB.uniqueHashes);

        Set<Long> union = new HashSet<>(outA.uniqueHashes);
        union.addAll(outB.uniqueHashes);

        SimilarityPair pair = SimilarityPair.builder()
                .id(1L)
                .similarityRate(result.getSimilarityRate())
                .sharedHashesCount(result.getSharedHashesCount())
                .isFlagged(result.isFlagged())
                .build();

        List<PlagiarismEvidence> evidences = evidenceGenerator.generateEvidence(pair, outA.fingerprints, outB.fingerprints);

        System.out.println("==================================================");
        System.out.println("CASE: " + caseName);
        System.out.println("--------------------------------------------------");
        System.out.println("Code A: nodes=" + outA.nodes.size() + ", kgrams=" + outA.kgrams.size() +
                ", winnowed=" + outA.winnowed.size() + ", uniqueHashes=" + outA.uniqueHashes.size());
        System.out.println("Code B: nodes=" + outB.nodes.size() + ", kgrams=" + outB.kgrams.size() +
                ", winnowed=" + outB.winnowed.size() + ", uniqueHashes=" + outB.uniqueHashes.size());
        System.out.println("Intersection (shared): " + result.getSharedHashesCount());
        System.out.println("Union: " + union.size());
        System.out.println("Similarity Rate: " + result.getSimilarityRate() + "%");
        System.out.println("Is Flagged (>= 70.00%): " + result.isFlagged());
        System.out.println("Evidence count: " + evidences.size());
        if (!evidences.isEmpty()) {
            for (int i = 0; i < Math.min(3, evidences.size()); i++) {
                PlagiarismEvidence ev = evidences.get(i);
                System.out.println("  Sample Evidence " + (i + 1) + ": type=" + ev.getEvidenceType() +
                        ", A lines=" + ev.getStartLineA() + "-" + ev.getEndLineA() +
                        ", B lines=" + ev.getStartLineB() + "-" + ev.getEndLineB());
            }
        }
        System.out.println("==================================================\n");
    }

    @Test
    @DisplayName("Fixture 1: Identical Code")
    void testIdenticalCode() {
        String codeA = """
                public class MathUtils {
                    public static int sumArray(int[] numbers) {
                        int total = 0;
                        for (int i = 0; i < numbers.length; i++) {
                            total += numbers[i];
                        }
                        return total;
                    }
                    public static int findMax(int[] numbers) {
                        int max = numbers[0];
                        for (int i = 1; i < numbers.length; i++) {
                            if (numbers[i] > max) {
                                max = numbers[i];
                            }
                        }
                        return max;
                    }
                }
                """;
        printComparison("1. Identical Code", codeA, codeA);
    }

    @Test
    @DisplayName("Fixture 2: Same code with renamed variables/methods and added comments")
    void testRenamedAndComments() {
        String codeA = """
                public class MathUtils {
                    public static int sumArray(int[] numbers) {
                        int total = 0;
                        for (int i = 0; i < numbers.length; i++) {
                            total += numbers[i];
                        }
                        return total;
                    }
                    public static int findMax(int[] numbers) {
                        int max = numbers[0];
                        for (int i = 1; i < numbers.length; i++) {
                            if (numbers[i] > max) {
                                max = numbers[i];
                            }
                        }
                        return max;
                    }
                }
                """;
        String codeB = """
                // Utility class for array operations
                public class ArrayHelper {
                    // Calculates total sum of items
                    public static int computeSum(int[] values) {
                        /* accumulate in totalSum */
                        int totalSum = 0;
                        for (int idx = 0; idx < values.length; idx++) {
                            totalSum += values[idx];
                        }
                        return totalSum;
                    }
                    // Finds highest value in list
                    public static int locatePeak(int[] data) {
                        int peakVal = data[0];
                        for (int k = 1; k < data.length; k++) {
                            if (data[k] > peakVal) {
                                peakVal = data[k];
                            }
                        }
                        return peakVal;
                    }
                }
                """;
        printComparison("2. Renamed Variables/Methods & Comments", codeA, codeB);
    }

    @Test
    @DisplayName("Fixture 3: Same code with method declarations reordered")
    void testMethodReordering() {
        String codeA = """
                public class MathUtils {
                    public static int sumArray(int[] numbers) {
                        int total = 0;
                        for (int i = 0; i < numbers.length; i++) {
                            total += numbers[i];
                        }
                        return total;
                    }
                    public static int findMax(int[] numbers) {
                        int max = numbers[0];
                        for (int i = 1; i < numbers.length; i++) {
                            if (numbers[i] > max) {
                                max = numbers[i];
                            }
                        }
                        return max;
                    }
                }
                """;
        String codeB = """
                public class MathUtils {
                    public static int findMax(int[] numbers) {
                        int max = numbers[0];
                        for (int i = 1; i < numbers.length; i++) {
                            if (numbers[i] > max) {
                                max = numbers[i];
                            }
                        }
                        return max;
                    }
                    public static int sumArray(int[] numbers) {
                        int total = 0;
                        for (int i = 0; i < numbers.length; i++) {
                            total += numbers[i];
                        }
                        return total;
                    }
                }
                """;
        printComparison("3. Method Declarations Reordered (Longer methods)", codeA, codeB);
    }

    @Test
    @DisplayName("Fixture 3b: Short methods reordered")
    void testShortMethodsReordered() {
        String codeA = """
                public class ShortUtils {
                    public static int add(int a, int b) {
                        return a + b;
                    }
                    public static int sub(int a, int b) {
                        return a - b;
                    }
                    public static int mul(int a, int b) {
                        return a * b;
                    }
                    public static int div(int a, int b) {
                        return a / b;
                    }
                }
                """;
        String codeB = """
                public class ShortUtils {
                    public static int mul(int a, int b) {
                        return a * b;
                    }
                    public static int add(int a, int b) {
                        return a + b;
                    }
                    public static int div(int a, int b) {
                        return a / b;
                    }
                    public static int sub(int a, int b) {
                        return a - b;
                    }
                }
                """;
        printComparison("3b. Short Methods Reordered (Boundary sensitivity)", codeA, codeB);
    }

    @Test
    @DisplayName("Fixture 3c: Asymmetric methods reordered")
    void testAsymmetricMethodsReordered() {
        String codeA = """
                public class AsymUtils {
                    public static int computeThreshold(int[] arr, int limit) {
                        int count = 0;
                        for (int i = 0; i < arr.length; i++) {
                            if (arr[i] > limit) {
                                count++;
                            }
                        }
                        return count;
                    }
                    public static double evaluateDiscount(double base, boolean vip) {
                        if (vip) {
                            return base * 0.8;
                        } else {
                            return base * 0.95;
                        }
                    }
                }
                """;
        String codeB = """
                public class AsymUtils {
                    public static double evaluateDiscount(double base, boolean vip) {
                        if (vip) {
                            return base * 0.8;
                        } else {
                            return base * 0.95;
                        }
                    }
                    public static int computeThreshold(int[] arr, int limit) {
                        int count = 0;
                        for (int i = 0; i < arr.length; i++) {
                            if (arr[i] > limit) {
                                count++;
                            }
                        }
                        return count;
                    }
                }
                """;
        printComparison("3c. Asymmetric Methods Reordered", codeA, codeB);
    }

    @Test
    @DisplayName("Fixture 4: Equivalent logic with for rewritten as while")
    void testForToWhileRewrite() {
        String codeA = """
                public class MathUtils {
                    public static int sumArray(int[] numbers) {
                        int total = 0;
                        for (int i = 0; i < numbers.length; i++) {
                            total += numbers[i];
                        }
                        return total;
                    }
                }
                """;
        String codeB = """
                public class MathUtils {
                    public static int sumArray(int[] numbers) {
                        int total = 0;
                        int i = 0;
                        while (i < numbers.length) {
                            total += numbers[i];
                            i++;
                        }
                        return total;
                    }
                }
                """;
        printComparison("4. For-Loop Rewritten as While-Loop", codeA, codeB);
    }

    @Test
    @DisplayName("Fixture 5: Small literal-value changes")
    void testLiteralChanges() {
        String codeA = """
                public class PricingService {
                    public static double calculateDiscount(double price, int qty) {
                        if (qty > 10) {
                            return price * 0.10;
                        }
                        return price * 0.02;
                    }
                }
                """;
        String codeB = """
                public class PricingService {
                    public static double calculateDiscount(double price, int qty) {
                        if (qty > 50) {
                            return price * 0.25;
                        }
                        return price * 0.05;
                    }
                }
                """;
        printComparison("5. Small Literal-Value Changes", codeA, codeB);
    }

    @Test
    @DisplayName("Fixture 6: Different logic with superficially similar structure")
    void testDifferentLogicSuperficialStructure() {
        String codeA = """
                public class AlgorithmA {
                    public static int process(int[] data) {
                        int count = 0;
                        for (int i = 0; i < data.length; i++) {
                            if (data[i] % 2 == 0) {
                                count++;
                            }
                        }
                        return count;
                    }
                }
                """;
        String codeB = """
                public class AlgorithmB {
                    public static String formatNames(String[] names) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < names.length; i++) {
                            sb.append(names[i].toUpperCase());
                            sb.append(",");
                        }
                        return sb.toString();
                    }
                }
                """;
        printComparison("6. Different Logic Superficially Similar", codeA, codeB);
    }
}
