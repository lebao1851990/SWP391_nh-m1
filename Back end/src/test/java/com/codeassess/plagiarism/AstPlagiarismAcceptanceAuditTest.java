package com.codeassess.plagiarism;

import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.plagiarism.entity.AstFingerprint;
import com.codeassess.plagiarism.entity.PlagiarismEvidence;
import com.codeassess.plagiarism.entity.SimilarityPair;
import com.codeassess.plagiarism.normalizer.AstNormalizer;
import com.codeassess.plagiarism.parser.AstNode;
import com.codeassess.plagiarism.parser.JavaAstParser;
import com.codeassess.plagiarism.repository.AstFingerprintRepository;
import com.codeassess.plagiarism.repository.ExamMatrixCoordinationRepository;
import com.codeassess.plagiarism.repository.PlagiarismEvidenceRepository;
import com.codeassess.plagiarism.repository.SimilarityPairRepository;
import com.codeassess.plagiarism.service.AstPlagiarismService;
import com.codeassess.plagiarism.service.SimilarityMatrixService;
import com.codeassess.plagiarism.similarity.EvidenceGenerator;
import com.codeassess.plagiarism.similarity.JaccardSimilarityCalculator;
import com.codeassess.plagiarism.similarity.SimilarityResult;
import com.codeassess.plagiarism.tokenizer.KGram;
import com.codeassess.plagiarism.tokenizer.KGramGenerator;
import com.codeassess.plagiarism.winnowing.FingerprintHasher;
import com.codeassess.plagiarism.winnowing.HashedKGram;
import com.codeassess.plagiarism.winnowing.Winnower;
import com.codeassess.submission.dto.ExtractedFile;
import com.codeassess.submission.dto.ExtractedWorkspace;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class AstPlagiarismAcceptanceAuditTest {

    @Autowired
    private JavaAstParser parser;

    @Autowired
    private AstNormalizer normalizer;

    @Autowired
    private KGramGenerator kGramGenerator;

    @Autowired
    private FingerprintHasher hasher;

    @Autowired
    private Winnower winnower;

    @Autowired
    private JaccardSimilarityCalculator similarityCalculator;

    @Autowired
    private EvidenceGenerator evidenceGenerator;

    @Autowired
    private AstPlagiarismService astPlagiarismService;

    @Autowired
    private SimilarityMatrixService similarityMatrixService;

    @Autowired
    private ExamMatrixCoordinationRepository coordinationRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private AstFingerprintRepository fingerprintRepository;

    @Autowired
    private SimilarityPairRepository similarityPairRepository;

    @Autowired
    private PlagiarismEvidenceRepository evidenceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseClassRepository courseClassRepository;

    @Autowired
    private ExamRepository examRepository;

    private Set<Long> computeFingerprintHashes(String sourceCode) {
        if (sourceCode == null || sourceCode.trim().isEmpty()) {
            return Collections.emptySet();
        }
        CompilationUnit cu;
        try {
            cu = parser.parse(sourceCode);
        } catch (Exception e) {
            return Collections.emptySet();
        }
        List<AstNode> nodes = normalizer.normalize(cu);
        List<KGram> kgrams = kGramGenerator.generateKGrams(nodes);
        if (kgrams.isEmpty()) {
            return Collections.emptySet();
        }
        List<HashedKGram> hashed = new ArrayList<>();
        for (int i = 0; i < kgrams.size(); i++) {
            KGram kg = kgrams.get(i);
            long hash = hasher.hash(kg.getConcatenatedTypeString());
            hashed.add(new HashedKGram(hash, i, kg.getRepresentativeNodeType(), kg.getStartLine(), kg.getEndLine()));
        }
        List<HashedKGram> winnowed = winnower.winnow(hashed);
        return winnowed.stream().map(HashedKGram::getHash).collect(Collectors.toSet());
    }

    private String randomSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    // =========================================================================
    // SECTION 3: TEST CHỐNG NGỤY TRANG (ANTI-OBFUSCATION TESTS)
    // =========================================================================

    @Test
    @DisplayName("REQ-3.1: Variable renaming produces 100% similarity and flags plagiarism")
    void testAntitransformation_VariableRenamingProduces100PercentSimilarityAndFlagged() {
        String baseCode = """
                public class Calculator {
                    public int computeFactorial(int number) {
                        int accumulatedResult = 1;
                        for (int counter = 1; counter <= number; counter++) {
                            accumulatedResult *= counter;
                        }
                        return accumulatedResult;
                    }
                }
                """;

        String renamedCode = """
                public class Calculator {
                    public int computeFactorial(int n) {
                        int res = 1;
                        for (int i = 1; i <= n; i++) {
                            res *= i;
                        }
                        return res;
                    }
                }
                """;

        Set<Long> hashesBase = computeFingerprintHashes(baseCode);
        Set<Long> hashesRenamed = computeFingerprintHashes(renamedCode);

        assertThat(hashesBase).isNotEmpty();
        assertThat(hashesBase).isEqualTo(hashesRenamed);

        SimilarityResult result = similarityCalculator.calculate(hashesBase, hashesRenamed);
        assertThat(result.getSimilarityRate()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.isFlagged()).isTrue();
        assertThat(result.getSharedHashesCount()).isEqualTo(hashesBase.size());
    }

    @Test
    @DisplayName("REQ-3.2: Method and class renaming produces 100% similarity and flags plagiarism")
    void testAntitransformation_MethodRenamingProduces100PercentSimilarityAndFlagged() {
        String baseCode = """
                public class MathHelper {
                    public int calculateSum(int a, int b) {
                        int total = a + b;
                        if (total > 100) {
                            return total * 2;
                        }
                        return total;
                    }
                }
                """;

        String renamedCode = """
                public class UtilityClass {
                    public int executeAddition(int paramOne, int paramTwo) {
                        int output = paramOne + paramTwo;
                        if (output > 100) {
                            return output * 2;
                        }
                        return output;
                    }
                }
                """;

        Set<Long> hashesBase = computeFingerprintHashes(baseCode);
        Set<Long> hashesRenamed = computeFingerprintHashes(renamedCode);

        assertThat(hashesBase).isNotEmpty();
        assertThat(hashesBase).isEqualTo(hashesRenamed);

        SimilarityResult result = similarityCalculator.calculate(hashesBase, hashesRenamed);
        assertThat(result.getSimilarityRate()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.isFlagged()).isTrue();
    }

    @Test
    @DisplayName("REQ-3.3: Comments, Javadoc and whitespace variations do not alter AST structure or fingerprints")
    void testAntitransformation_CommentsAndWhitespaceDoNotAlterFingerprints() {
        String baseCode = """
                public class Sorter {
                    public static void bubbleSort(int[] arr) {
                        int n = arr.length;
                        for (int i = 0; i < n - 1; i++) {
                            for (int j = 0; j < n - i - 1; j++) {
                                if (arr[j] > arr[j + 1]) {
                                    int temp = arr[j];
                                    arr[j] = arr[j + 1];
                                    arr[j + 1] = temp;
                                }
                            }
                        }
                    }
                }
                """;

        String formattedWithComments = """
                /**
                 * Javadoc comment for Sorter.
                 * @author Student
                 */
                public class Sorter {
                    // Method comment
                    public static void bubbleSort(int[] arr) {
                        /*
                         * Multiline block comment
                         */
                        int n = arr.length;
                        
                        
                        for (int i = 0; i < n - 1; i++) {
                            // Inner loop with tabs
                            for (int j = 0; j < n - i - 1; j++) {
                                if (arr[j] > arr[j + 1]) {
                                    /* swap */
                                    int temp = arr[j];
                                    arr[j] = arr[j + 1];
                                    arr[j + 1] = temp;
                                }
                            }
                        }
                    }
                }
                """;

        Set<Long> hashesBase = computeFingerprintHashes(baseCode);
        Set<Long> hashesCommented = computeFingerprintHashes(formattedWithComments);

        assertThat(hashesBase).isNotEmpty();
        assertThat(hashesBase).isEqualTo(hashesCommented);

        SimilarityResult result = similarityCalculator.calculate(hashesBase, hashesCommented);
        assertThat(result.getSimilarityRate()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.isFlagged()).isTrue();
    }

    @Test
    @DisplayName("REQ-3.4: Equivalent control flow rewrite (for <-> while) causes expected token disruption under K=5")
    void testAntitransformation_EquivalentRewriteForToWhileAnalyzed() {
        String forLoopCode = """
                public class LoopDemo {
                    public int sumArray(int[] data) {
                        int sum = 0;
                        for (int idx = 0; idx < data.length; idx++) {
                            sum += data[idx];
                        }
                        return sum;
                    }
                }
                """;

        String whileLoopCode = """
                public class LoopDemo {
                    public int sumArray(int[] data) {
                        int sum = 0;
                        int idx = 0;
                        while (idx < data.length) {
                            sum += data[idx];
                            idx++;
                        }
                        return sum;
                    }
                }
                """;

        Set<Long> hashesFor = computeFingerprintHashes(forLoopCode);
        Set<Long> hashesWhile = computeFingerprintHashes(whileLoopCode);

        // Verification of specification: ForStmt vs WhileStmt alters AST node sequence.
        // Under K=5 and W=4, AST structure differs, so similarity drops significantly.
        SimilarityResult result = similarityCalculator.calculate(hashesFor, hashesWhile);
        
        // Asserting that similarity rate is below the 70.00% threshold as documented in baseline specifications
        assertThat(result.getSimilarityRate()).isLessThan(new BigDecimal("70.00"));
        assertThat(result.isFlagged()).isFalse();
    }

    @Test
    @DisplayName("REQ-3.5: Distinct algorithmic logic produces low similarity (< 25%) and prevents false positives")
    void testAntitransformation_DistinctLogicProducesLowSimilarityAndNoFalsePositive() {
        // Binary search algorithm
        String binarySearchCode = """
                public class BinarySearch {
                    public int search(int[] arr, int target) {
                        int low = 0;
                        int high = arr.length - 1;
                        while (low <= high) {
                            int mid = low + (high - low) / 2;
                            if (arr[mid] == target) {
                                return mid;
                            } else if (arr[mid] < target) {
                                low = mid + 1;
                            } else {
                                high = mid - 1;
                            }
                        }
                        return -1;
                    }
                }
                """;

        // Matrix multiplication algorithm
        String matrixMultiplyCode = """
                public class MatrixMultiply {
                    public int[][] multiply(int[][] a, int[][] b) {
                        int rows = a.length;
                        int cols = b[0].length;
                        int common = b.length;
                        int[][] result = new int[rows][cols];
                        for (int i = 0; i < rows; i++) {
                            for (int j = 0; j < cols; j++) {
                                for (int k = 0; k < common; k++) {
                                    result[i][j] += a[i][k] * b[k][j];
                                }
                            }
                        }
                        return result;
                    }
                }
                """;

        Set<Long> hashesBS = computeFingerprintHashes(binarySearchCode);
        Set<Long> hashesMM = computeFingerprintHashes(matrixMultiplyCode);

        assertThat(hashesBS).isNotEmpty();
        assertThat(hashesMM).isNotEmpty();

        SimilarityResult result = similarityCalculator.calculate(hashesBS, hashesMM);
        assertThat(result.getSimilarityRate()).isLessThan(new BigDecimal("25.00"));
        assertThat(result.isFlagged()).isFalse();
    }

    @Test
    @DisplayName("REQ-3.6: Unparseable source code (syntax errors) is handled gracefully without unhandled exceptions")
    void testAntitransformation_UnparseableSyntaxErrorHandledGracefully() {
        String brokenCode = "public class Broken { void unclosedMethod( { invalid syntax @#$! %^&*";

        String suffix = randomSuffix();
        User student = userRepository.save(User.builder().email("stu_broken_" + suffix + "@test.com").fullName("Student Broken").passwordHash("pwd").role(Role.STUDENT).build());
        User teacher = userRepository.save(User.builder().email("tea_broken_" + suffix + "@test.com").fullName("Teacher Broken").passwordHash("pwd").role(Role.TEACHER).build());
        CourseClass cc = courseClassRepository.save(CourseClass.builder().code("CC_B_" + suffix).name("Class B").teacher(teacher).semester("FA24").build());
        Exam exam = examRepository.save(Exam.builder().title("Exam Broken " + suffix).courseClass(cc).status("ACTIVE").build());

        Submission sub = submissionRepository.save(Submission.builder()
                .exam(exam)
                .student(student)
                .status("EVALUATED")
                .sourceCode(brokenCode)
                .build());

        // Must not throw an unhandled exception
        assertDoesNotThrow(() -> astPlagiarismService.generateFingerprintsForSubmission(sub.getId()));

        List<AstFingerprint> fps = fingerprintRepository.findBySubmission_Id(sub.getId());
        assertThat(fps).isEmpty(); // No fingerprints saved for broken syntax
    }

    @Test
    @DisplayName("REQ-3.7: Multi-file / ZIP workspace fingerprint generation with sorted relative paths")
    void testAntitransformation_MultiFileZipWorkspaceFingerprintingAndSimilarity() throws Exception {
        Path tempDirA = Files.createTempDirectory("zip-audit-a-");
        Path fA1 = tempDirA.resolve("Main.java");
        Files.writeString(fA1, "public class Main { public static void main(String[] args) { new util.Helper().doWork(); } }");
        Path subDirA = tempDirA.resolve("util");
        Files.createDirectories(subDirA);
        Path fA2 = subDirA.resolve("Helper.java");
        Files.writeString(fA2, "package util; public class Helper { public int doWork() { int sum = 0; for(int i=0;i<10;i++) sum += i; return sum; } }");

        ExtractedWorkspace wsA = new ExtractedWorkspace(
                tempDirA,
                List.of(
                        new ExtractedFile("Main.java", fA1, Files.size(fA1), true),
                        new ExtractedFile("util/Helper.java", fA2, Files.size(fA2), true)
                ),
                1024L, "shaA"
        );

        String suffix = randomSuffix();
        User stuA = userRepository.save(User.builder().email("stu_zip_a_" + suffix + "@test.com").fullName("Student Zip A").passwordHash("pwd").role(Role.STUDENT).build());
        User teacher = userRepository.save(User.builder().email("tea_zip_" + suffix + "@test.com").fullName("Teacher Zip").passwordHash("pwd").role(Role.TEACHER).build());
        CourseClass cc = courseClassRepository.save(CourseClass.builder().code("CC_Z_" + suffix).name("Class Z").teacher(teacher).semester("FA24").build());
        Exam exam = examRepository.save(Exam.builder().title("Exam Zip " + suffix).courseClass(cc).status("ACTIVE").build());

        Submission subA = submissionRepository.save(Submission.builder()
                .exam(exam)
                .student(stuA)
                .status("EVALUATED")
                .submissionType("ZIP")
                .build());

        astPlagiarismService.generateFingerprintsForWorkspace(subA, wsA);

        List<AstFingerprint> fpsA = fingerprintRepository.findBySubmission_Id(subA.getId());
        assertThat(fpsA).isNotEmpty();

        // Verify that relative paths from multiple files are preserved
        boolean hasMain = fpsA.stream().anyMatch(fp -> "Main.java".equals(fp.getFilePath()));
        boolean hasHelper = fpsA.stream().anyMatch(fp -> "util/Helper.java".equals(fp.getFilePath()));
        assertThat(hasMain).isTrue();
        assertThat(hasHelper).isTrue();

        wsA.close();
    }

    @Test
    @DisplayName("REQ-3.8: Empty source code or sequence shorter than K=5 produces 0 fingerprints and 0.00% similarity")
    void testAntitransformation_EmptyOrInsufficientTokensProducesZeroSimilarity() {
        String tinyCode = "class T {}"; // Shorter than K=5 tokens
        Set<Long> hashesTiny = computeFingerprintHashes(tinyCode);
        Set<Long> hashesEmpty = computeFingerprintHashes("");

        assertThat(hashesTiny).isEmpty();
        assertThat(hashesEmpty).isEmpty();

        SimilarityResult result = similarityCalculator.calculate(hashesTiny, hashesEmpty);
        assertThat(result.getSimilarityRate()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(result.getSharedHashesCount()).isEqualTo(0);
        assertThat(result.isFlagged()).isFalse();
    }

    // =========================================================================
    // SECTION 4: KIỂM TRA SIMILARITY MATRIX END-TO-END
    // =========================================================================

    @Test
    @DisplayName("REQ-4.1: Multi-submission exam generates canonical strictly-ordered pairs with consistent metrics")
    void testSimilarityMatrixEndToEnd_MultiSubmissionsExamGeneratesCanonicalOrderedPairs() {
        String suffix = randomSuffix();
        User teacher = userRepository.save(User.builder().email("tea_m41_" + suffix + "@test.com").fullName("Teacher M41").passwordHash("pwd").role(Role.TEACHER).build());
        CourseClass cc = courseClassRepository.save(CourseClass.builder().code("CC_M41_" + suffix).name("Class M41").teacher(teacher).semester("FA24").build());
        Exam exam = examRepository.save(Exam.builder().title("Exam M41 " + suffix).courseClass(cc).status("ACTIVE").build());

        User stu1 = userRepository.save(User.builder().email("s1_" + suffix + "@test.com").fullName("S1").passwordHash("pwd").role(Role.STUDENT).build());
        User stu2 = userRepository.save(User.builder().email("s2_" + suffix + "@test.com").fullName("S2").passwordHash("pwd").role(Role.STUDENT).build());
        User stu3 = userRepository.save(User.builder().email("s3_" + suffix + "@test.com").fullName("S3").passwordHash("pwd").role(Role.STUDENT).build());

        String codeA = """
                public class SolA {
                    public int solve(int n) {
                        int sum = 0;
                        for (int i = 0; i < n; i++) {
                            sum += i;
                        }
                        return sum;
                    }
                }
                """;

        String codeB = """
                public class SolB {
                    public int solve(int limit) {
                        int total = 0;
                        for (int k = 0; k < limit; k++) {
                            total += k;
                        }
                        return total;
                    }
                }
                """; // Renamed version of A (100% match)

        String codeC = """
                public class SolC {
                    public boolean check(String s) {
                        return s != null && s.length() > 5;
                    }
                }
                """; // Completely different code

        Submission sub1 = submissionRepository.save(Submission.builder().exam(exam).student(stu1).status("EVALUATED").sourceCode(codeA).build());
        Submission sub2 = submissionRepository.save(Submission.builder().exam(exam).student(stu2).status("EVALUATED").sourceCode(codeB).build());
        Submission sub3 = submissionRepository.save(Submission.builder().exam(exam).student(stu3).status("EVALUATED").sourceCode(codeC).build());

        // Generate fingerprints for all 3 submissions
        astPlagiarismService.generateFingerprintsForSubmission(sub1.getId());
        astPlagiarismService.generateFingerprintsForSubmission(sub2.getId());
        astPlagiarismService.generateFingerprintsForSubmission(sub3.getId());

        // Refresh matrix
        similarityMatrixService.generateMatrixForExam(exam.getId());

        // Exactly C(3, 2) = 3 pairs must exist
        List<SimilarityPair> pairs = similarityPairRepository.findByExam_Id(exam.getId());
        assertThat(pairs).hasSize(3);

        // Verify ID order convention: submissionA.id < submissionB.id for ALL pairs
        for (SimilarityPair p : pairs) {
            assertThat(p.getSubmissionA().getId()).isLessThan(p.getSubmissionB().getId());
            assertThat(p.getSimilarityRate()).isNotNull();
            assertThat(p.getSharedHashesCount()).isNotNull();
        }

        // Identify pair between sub1 and sub2 (which should be flagged 100%)
        SimilarityPair pair12 = pairs.stream()
                .filter(p -> (p.getSubmissionA().getId().equals(sub1.getId()) && p.getSubmissionB().getId().equals(sub2.getId()))
                          || (p.getSubmissionA().getId().equals(sub2.getId()) && p.getSubmissionB().getId().equals(sub1.getId())))
                .findFirst().orElseThrow();

        assertThat(pair12.getSimilarityRate()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(pair12.getIsFlagged()).isTrue();
        assertThat(pair12.getSharedHashesCount()).isGreaterThan(0);

        // Identify pair involving sub3 (different code)
        SimilarityPair pair13 = pairs.stream()
                .filter(p -> (p.getSubmissionA().getId().equals(sub1.getId()) && p.getSubmissionB().getId().equals(sub3.getId()))
                          || (p.getSubmissionA().getId().equals(sub3.getId()) && p.getSubmissionB().getId().equals(sub1.getId())))
                .findFirst().orElseThrow();

        assertThat(pair13.getSimilarityRate()).isLessThan(new BigDecimal("70.00"));
        assertThat(pair13.getIsFlagged()).isFalse();
    }

    @Test
    @DisplayName("REQ-4.2: In-place refresh preserves primary key and faculty adjudication fields without duplicates")
    void testSimilarityMatrixEndToEnd_RefreshAndRetryMaintainsInPlacePairsWithoutDuplicates() {
        String suffix = randomSuffix();
        User teacher = userRepository.save(User.builder().email("tea_m42_" + suffix + "@test.com").fullName("Teacher M42").passwordHash("pwd").role(Role.TEACHER).build());
        CourseClass cc = courseClassRepository.save(CourseClass.builder().code("CC_M42_" + suffix).name("Class M42").teacher(teacher).semester("FA24").build());
        Exam exam = examRepository.save(Exam.builder().title("Exam M42 " + suffix).courseClass(cc).status("ACTIVE").build());

        User stuA = userRepository.save(User.builder().email("sa_m42_" + suffix + "@test.com").fullName("Stu A").passwordHash("pwd").role(Role.STUDENT).build());
        User stuB = userRepository.save(User.builder().email("sb_m42_" + suffix + "@test.com").fullName("Stu B").passwordHash("pwd").role(Role.STUDENT).build());

        Submission sub1 = submissionRepository.save(Submission.builder().exam(exam).student(stuA).status("EVALUATED").sourceCode("class A { int f(int x){ return x*2; } }").build());
        Submission sub2 = submissionRepository.save(Submission.builder().exam(exam).student(stuB).status("EVALUATED").sourceCode("class B { int f(int y){ return y*2; } }").build());

        astPlagiarismService.generateFingerprintsForSubmission(sub1.getId());
        astPlagiarismService.generateFingerprintsForSubmission(sub2.getId());

        // First generation
        similarityMatrixService.generateMatrixForExam(exam.getId());

        List<SimilarityPair> initialPairs = similarityPairRepository.findByExam_Id(exam.getId());
        assertThat(initialPairs).hasSize(1);
        SimilarityPair initialPair = initialPairs.get(0);
        Long pairId = initialPair.getId();

        // Simulate teacher adjudication
        initialPair.setAdjudicationStatus("FLAG_CONFIRMED");
        initialPair.setAdjudicationNotes("Student confirmed collaboration");
        initialPair.setAdjudicatedBy(teacher);
        initialPair.setAdjudicatedAt(java.time.LocalDateTime.now());
        similarityPairRepository.save(initialPair);

        // Trigger refresh request
        coordinationRepository.requestMatrixRefresh(exam.getId());
        similarityMatrixService.generateMatrixForExam(exam.getId());

        // Verify that NO duplicate pairs were created and pair ID & adjudication are preserved
        List<SimilarityPair> refreshedPairs = similarityPairRepository.findByExam_Id(exam.getId());
        assertThat(refreshedPairs).hasSize(1);
        SimilarityPair refreshedPair = refreshedPairs.get(0);

        assertThat(refreshedPair.getId()).isEqualTo(pairId);
        assertThat(refreshedPair.getAdjudicationStatus()).isEqualTo("FLAG_CONFIRMED");
        assertThat(refreshedPair.getAdjudicationNotes()).isEqualTo("Student confirmed collaboration");
        assertThat(refreshedPair.getAdjudicatedBy().getId()).isEqualTo(teacher.getId());
    }

    @Test
    @DisplayName("REQ-4.3: Submission update replaces stale plagiarism evidence cleanly")
    void testSimilarityMatrixEndToEnd_SubmissionUpdateReplacesStaleEvidence() {
        String suffix = randomSuffix();
        User teacher = userRepository.save(User.builder().email("tea_m43_" + suffix + "@test.com").fullName("Teacher M43").passwordHash("pwd").role(Role.TEACHER).build());
        CourseClass cc = courseClassRepository.save(CourseClass.builder().code("CC_M43_" + suffix).name("Class M43").teacher(teacher).semester("FA24").build());
        Exam exam = examRepository.save(Exam.builder().title("Exam M43 " + suffix).courseClass(cc).status("ACTIVE").build());

        User stuA = userRepository.save(User.builder().email("sa_m43_" + suffix + "@test.com").fullName("Stu A").passwordHash("pwd").role(Role.STUDENT).build());
        User stuB = userRepository.save(User.builder().email("sb_m43_" + suffix + "@test.com").fullName("Stu B").passwordHash("pwd").role(Role.STUDENT).build());

        Submission sub1 = submissionRepository.save(Submission.builder().exam(exam).student(stuA).status("EVALUATED").sourceCode("class A { int f(int x){ return x*2; } }").build());
        Submission sub2 = submissionRepository.save(Submission.builder().exam(exam).student(stuB).status("EVALUATED").sourceCode("class B { int f(int y){ return y*2; } }").build());

        astPlagiarismService.generateFingerprintsForSubmission(sub1.getId());
        astPlagiarismService.generateFingerprintsForSubmission(sub2.getId());

        similarityMatrixService.generateMatrixForExam(exam.getId());

        List<SimilarityPair> pairs = similarityPairRepository.findByExam_Id(exam.getId());
        assertThat(pairs).hasSize(1);
        SimilarityPair pair = pairs.get(0);

        List<PlagiarismEvidence> initialEvidences = evidenceRepository.findBySimilarityPair_Id(pair.getId());
        assertThat(initialEvidences).isNotEmpty();
        Set<String> initialEvidenceIds = initialEvidences.stream().map(PlagiarismEvidence::getId).collect(Collectors.toSet());

        // Student B resubmits completely different code (e.g. string check)
        sub2.setSourceCode("""
                public class SolB {
                    public boolean isLong(String s) {
                        return s != null && s.length() > 10;
                    }
                }
                """);
        submissionRepository.save(sub2);

        // Regenerate fingerprints for sub2
        astPlagiarismService.generateFingerprintsForSubmission(sub2.getId());

        // Refresh matrix
        coordinationRepository.requestMatrixRefresh(exam.getId());
        similarityMatrixService.generateMatrixForExam(exam.getId());

        // Evidence should be updated, and initial stale evidence IDs must no longer exist
        List<PlagiarismEvidence> newEvidences = evidenceRepository.findBySimilarityPair_Id(pair.getId());
        Set<String> newEvidenceIds = newEvidences.stream().map(PlagiarismEvidence::getId).collect(Collectors.toSet());

        // Stale evidence from old run is cleaned up
        for (String oldId : initialEvidenceIds) {
            assertThat(newEvidenceIds).doesNotContain(oldId);
        }
    }
}
