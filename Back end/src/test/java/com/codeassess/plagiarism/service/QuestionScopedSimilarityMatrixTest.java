package com.codeassess.plagiarism.service;

import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.entity.ExamQuestion;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.plagiarism.entity.AstFingerprint;
import com.codeassess.plagiarism.entity.ExamMatrixCoordination;
import com.codeassess.plagiarism.entity.PlagiarismEvidence;
import com.codeassess.plagiarism.entity.SimilarityPair;
import com.codeassess.plagiarism.repository.AstFingerprintRepository;
import com.codeassess.plagiarism.repository.ExamMatrixCoordinationRepository;
import com.codeassess.plagiarism.repository.PlagiarismEvidenceRepository;
import com.codeassess.plagiarism.repository.SimilarityPairRepository;
import com.codeassess.plagiarism.similarity.EvidenceGenerator;
import com.codeassess.plagiarism.similarity.JaccardSimilarityCalculator;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class QuestionScopedSimilarityMatrixTest {

    @Mock
    private ExamRepository examRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private AstFingerprintRepository fingerprintRepository;

    @Mock
    private SimilarityPairRepository similarityPairRepository;

    @Mock
    private PlagiarismEvidenceRepository evidenceRepository;

    @Mock
    private ExamMatrixCoordinationRepository coordinationRepository;

    private JaccardSimilarityCalculator similarityCalculator;
    private EvidenceGenerator evidenceGenerator;
    private SimilarityMatrixService similarityMatrixService;

    private Exam testExam;
    private ExamQuestion question1;
    private ExamQuestion question2;
    private User studentA;
    private User studentB;
    private User studentC;

    @BeforeEach
    void setUp() {
        similarityCalculator = new JaccardSimilarityCalculator();
        evidenceGenerator = new EvidenceGenerator();

        similarityMatrixService = new SimilarityMatrixService(
                examRepository,
                submissionRepository,
                fingerprintRepository,
                similarityPairRepository,
                evidenceRepository,
                similarityCalculator,
                evidenceGenerator,
                coordinationRepository,
                null // null transactionManager executes directly
        );

        testExam = Exam.builder()
                .id("exam-mq-10")
                .title("Multi-Question Midterm")
                .build();

        question1 = ExamQuestion.builder()
                .id("q-algo-1")
                .exam(testExam)
                .title("Question 1: Binary Search")
                .build();

        question2 = ExamQuestion.builder()
                .id("q-algo-2")
                .exam(testExam)
                .title("Question 2: Graph DFS")
                .build();

        studentA = User.builder().id("student-A").fullName("Alice").build();
        studentB = User.builder().id("student-B").fullName("Bob").build();
        studentC = User.builder().id("student-C").fullName("Charlie").build();
    }

    @Test
    @DisplayName("Invariant 1: Two submissions for the same question are compared and pair is generated")
    void testTwoSubmissionsForSameQuestion_AreCompared() {
        Submission subA1 = Submission.builder()
                .id("sub-A-q1")
                .exam(testExam)
                .question(question1)
                .student(studentA)
                .build();

        Submission subB1 = Submission.builder()
                .id("sub-B-q1")
                .exam(testExam)
                .question(question1)
                .student(studentB)
                .build();

        List<AstFingerprint> fpA = List.of(
                AstFingerprint.builder().hashValue(1001L).nodeType("IfStmt").startLine(1).endLine(5).build(),
                AstFingerprint.builder().hashValue(1002L).nodeType("WhileStmt").startLine(6).endLine(10).build()
        );
        List<AstFingerprint> fpB = List.of(
                AstFingerprint.builder().hashValue(1001L).nodeType("IfStmt").startLine(2).endLine(6).build(),
                AstFingerprint.builder().hashValue(1002L).nodeType("WhileStmt").startLine(7).endLine(11).build()
        );

        when(examRepository.findById("exam-mq-10")).thenReturn(Optional.of(testExam));
        when(submissionRepository.findByExam_Id("exam-mq-10")).thenReturn(List.of(subA1, subB1));
        when(similarityPairRepository.findByExam_Id("exam-mq-10")).thenReturn(Collections.emptyList());
        when(fingerprintRepository.findBySubmission_Id("sub-A-q1")).thenReturn(fpA);
        when(fingerprintRepository.findBySubmission_Id("sub-B-q1")).thenReturn(fpB);

        SimilarityMatrixService.MatrixCalculationPlan plan = similarityMatrixService.computeCalculation("exam-mq-10");

        assertThat(plan.inserts()).hasSize(1);
        assertThat(plan.updates()).isEmpty();

        SimilarityMatrixService.PairInsertPlan insert = plan.inserts().get(0);
        assertThat(insert.subA().getId()).isEqualTo("sub-A-q1");
        assertThat(insert.subB().getId()).isEqualTo("sub-B-q1");
        assertThat(insert.question()).isEqualTo(question1);
        assertThat(insert.similarityRate()).isGreaterThan(BigDecimal.ZERO);
        assertThat(insert.sharedHashesCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Invariant 2: Two submissions for different questions in the same exam are NOT compared")
    void testTwoSubmissionsForDifferentQuestions_AreNotCompared() {
        Submission subA_Q1 = Submission.builder()
                .id("sub-A-q1")
                .exam(testExam)
                .question(question1)
                .student(studentA)
                .build();

        // Bob submits for Question 2 with identical fingerprints
        Submission subB_Q2 = Submission.builder()
                .id("sub-B-q2")
                .exam(testExam)
                .question(question2)
                .student(studentB)
                .build();

        List<AstFingerprint> identicalFp = List.of(
                AstFingerprint.builder().hashValue(5555L).nodeType("ForStmt").startLine(1).endLine(10).build()
        );

        when(examRepository.findById("exam-mq-10")).thenReturn(Optional.of(testExam));
        when(submissionRepository.findByExam_Id("exam-mq-10")).thenReturn(List.of(subA_Q1, subB_Q2));
        when(similarityPairRepository.findByExam_Id("exam-mq-10")).thenReturn(Collections.emptyList());
        when(fingerprintRepository.findBySubmission_Id("sub-A-q1")).thenReturn(identicalFp);
        when(fingerprintRepository.findBySubmission_Id("sub-B-q2")).thenReturn(identicalFp);

        SimilarityMatrixService.MatrixCalculationPlan plan = similarityMatrixService.computeCalculation("exam-mq-10");

        // Cross-question comparisons must never produce similarity pairs
        assertThat(plan.inserts()).isEmpty();
        assertThat(plan.updates()).isEmpty();
    }

    @Test
    @DisplayName("Invariant 3: Submission from one question cannot create evidence against another question")
    void testCrossQuestion_CannotCreateEvidenceAgainstAnotherQuestion() {
        // Alice and Bob submit for Question 1
        Submission subA_Q1 = Submission.builder().id("sub-A-q1").exam(testExam).question(question1).student(studentA).build();
        Submission subB_Q1 = Submission.builder().id("sub-B-q1").exam(testExam).question(question1).student(studentB).build();
        // Charlie submits for Question 2 with identical fingerprints
        Submission subC_Q2 = Submission.builder().id("sub-C-q2").exam(testExam).question(question2).student(studentC).build();

        List<AstFingerprint> fp = List.of(
                AstFingerprint.builder().hashValue(9999L).nodeType("Block").startLine(1).endLine(20).build()
        );

        when(examRepository.findById("exam-mq-10")).thenReturn(Optional.of(testExam));
        when(submissionRepository.findByExam_Id("exam-mq-10")).thenReturn(List.of(subA_Q1, subB_Q1, subC_Q2));
        when(similarityPairRepository.findByExam_Id("exam-mq-10")).thenReturn(Collections.emptyList());
        when(fingerprintRepository.findBySubmission_Id("sub-A-q1")).thenReturn(fp);
        when(fingerprintRepository.findBySubmission_Id("sub-B-q1")).thenReturn(fp);
        when(fingerprintRepository.findBySubmission_Id("sub-C-q2")).thenReturn(fp);

        SimilarityMatrixService.MatrixCalculationPlan plan = similarityMatrixService.computeCalculation("exam-mq-10");

        assertThat(plan.inserts()).hasSize(1);
        SimilarityMatrixService.PairInsertPlan insert = plan.inserts().get(0);
        assertThat(insert.subA().getId()).isEqualTo("sub-A-q1");
        assertThat(insert.subB().getId()).isEqualTo("sub-B-q1");
        assertThat(insert.question()).isEqualTo(question1);

        // Simulate reconciliation commit
        ExamMatrixCoordination coord = ExamMatrixCoordination.builder()
                .examId("exam-mq-10")
                .lockedGeneration(1L)
                .leaseToken("token-good")
                .leaseExpiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        when(coordinationRepository.findByExamIdForUpdate("exam-mq-10")).thenReturn(Optional.of(coord));

        SimilarityPair savedPair = SimilarityPair.builder().id(77L).question(question1).build();
        when(similarityPairRepository.save(any(SimilarityPair.class))).thenReturn(savedPair);

        similarityMatrixService.commitReconciliation("exam-mq-10", 1L, "token-good", plan);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlagiarismEvidence>> evidenceCaptor = ArgumentCaptor.forClass(List.class);
        verify(evidenceRepository).saveAll(evidenceCaptor.capture());

        List<PlagiarismEvidence> savedEvidences = evidenceCaptor.getValue();
        assertThat(savedEvidences).isNotEmpty();
        for (PlagiarismEvidence ev : savedEvidences) {
            assertThat(ev.getSimilarityPair()).isEqualTo(savedPair);
            assertThat(ev.getSimilarityPair().getQuestion()).isEqualTo(question1);
        }

        // Charlie's Question 2 submission is nowhere in any saved evidence or pair
        verify(similarityPairRepository, never()).save(argThat(pair ->
                pair.getSubmissionA().getId().equals("sub-C-q2") || pair.getSubmissionB().getId().equals("sub-C-q2")
        ));
    }

    @Test
    @DisplayName("Invariant 4: Legacy submissions with null question_id preserve single-question behavior")
    void testLegacySubmissionsWithNullQuestionId_PreserveExistingBehavior() {
        Submission subLegA = Submission.builder()
                .id("sub-leg-A")
                .exam(testExam)
                .question(null) // legacy exam submission
                .student(studentA)
                .build();

        Submission subLegB = Submission.builder()
                .id("sub-leg-B")
                .exam(testExam)
                .question(null)
                .student(studentB)
                .build();

        List<AstFingerprint> fp = List.of(
                AstFingerprint.builder().hashValue(777L).nodeType("MethodDecl").startLine(1).endLine(10).build()
        );

        when(examRepository.findById("exam-mq-10")).thenReturn(Optional.of(testExam));
        when(submissionRepository.findByExam_Id("exam-mq-10")).thenReturn(List.of(subLegA, subLegB));
        when(similarityPairRepository.findByExam_Id("exam-mq-10")).thenReturn(Collections.emptyList());
        when(fingerprintRepository.findBySubmission_Id("sub-leg-A")).thenReturn(fp);
        when(fingerprintRepository.findBySubmission_Id("sub-leg-B")).thenReturn(fp);

        SimilarityMatrixService.MatrixCalculationPlan plan = similarityMatrixService.computeCalculation("exam-mq-10");

        assertThat(plan.inserts()).hasSize(1);
        SimilarityMatrixService.PairInsertPlan insert = plan.inserts().get(0);
        assertThat(insert.subA().getId()).isEqualTo("sub-leg-A");
        assertThat(insert.subB().getId()).isEqualTo("sub-leg-B");
        assertThat(insert.question()).isNull();
        assertThat(insert.sharedHashesCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Invariant 5: Repeated matrix generation is idempotent and does not create duplicate pairs or evidence")
    void testRepeatedMatrixGeneration_IsIdempotent() {
        Submission subA = Submission.builder().id("sub-1").exam(testExam).question(question1).student(studentA).build();
        Submission subB = Submission.builder().id("sub-2").exam(testExam).question(question1).student(studentB).build();

        SimilarityPair existingPair = SimilarityPair.builder()
                .id(999L)
                .exam(testExam)
                .question(question1)
                .submissionA(subA)
                .submissionB(subB)
                .similarityRate(BigDecimal.valueOf(80.00))
                .sharedHashesCount(2)
                .isFlagged(true)
                .isActive(true)
                .build();

        List<AstFingerprint> fp = List.of(
                AstFingerprint.builder().hashValue(100L).nodeType("If").startLine(1).endLine(3).build()
        );

        when(examRepository.findById("exam-mq-10")).thenReturn(Optional.of(testExam));
        when(submissionRepository.findByExam_Id("exam-mq-10")).thenReturn(List.of(subA, subB));
        when(similarityPairRepository.findByExam_Id("exam-mq-10")).thenReturn(List.of(existingPair));
        when(fingerprintRepository.findBySubmission_Id("sub-1")).thenReturn(fp);
        when(fingerprintRepository.findBySubmission_Id("sub-2")).thenReturn(fp);

        SimilarityMatrixService.MatrixCalculationPlan plan = similarityMatrixService.computeCalculation("exam-mq-10");

        // Must update existing pair, NOT create a duplicate insert
        assertThat(plan.inserts()).isEmpty();
        assertThat(plan.updates()).hasSize(1);
        SimilarityMatrixService.PairUpdatePlan update = plan.updates().get(0);
        assertThat(update.pairId()).isEqualTo(999L);
        assertThat(update.question()).isEqualTo(question1);

        ExamMatrixCoordination coord = ExamMatrixCoordination.builder()
                .examId("exam-mq-10")
                .lockedGeneration(2L)
                .leaseToken("tok-2")
                .leaseExpiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        when(coordinationRepository.findByExamIdForUpdate("exam-mq-10")).thenReturn(Optional.of(coord));
        when(similarityPairRepository.getReferenceById(999L)).thenReturn(existingPair);

        similarityMatrixService.commitReconciliation("exam-mq-10", 2L, "tok-2", plan);

        // Verify update in place
        verify(similarityPairRepository).updateCalculatedMetricsWithQuestion(
                eq(999L), eq(question1), any(BigDecimal.class), eq(1), anyBoolean()
        );
        // Verify previous evidence cleaned before re-inserting
        verify(evidenceRepository).deleteBySimilarityPair_Id(999L);
        verify(evidenceRepository).saveAll(anyList());
        // Verify NO duplicate save occurred
        verify(similarityPairRepository, never()).save(any(SimilarityPair.class));
    }

    @Test
    @DisplayName("Invariant 6: Canonical pair ordering is always preserved: submissionA.id < submissionB.id")
    void testCanonicalPairOrdering_AlwaysPreserved() {
        // Given submissions with IDs where out-of-order insertion might occur
        Submission subZ = Submission.builder().id("sub-Z").exam(testExam).question(question1).student(studentA).build();
        Submission subA = Submission.builder().id("sub-A").exam(testExam).question(question1).student(studentB).build();

        when(examRepository.findById("exam-mq-10")).thenReturn(Optional.of(testExam));
        when(submissionRepository.findByExam_Id("exam-mq-10")).thenReturn(List.of(subZ, subA));
        when(similarityPairRepository.findByExam_Id("exam-mq-10")).thenReturn(Collections.emptyList());
        when(fingerprintRepository.findBySubmission_Id(anyString())).thenReturn(Collections.emptyList());

        SimilarityMatrixService.MatrixCalculationPlan plan = similarityMatrixService.computeCalculation("exam-mq-10");

        assertThat(plan.inserts()).hasSize(1);
        SimilarityMatrixService.PairInsertPlan insert = plan.inserts().get(0);
        assertThat(insert.subA().getId()).isEqualTo("sub-A");
        assertThat(insert.subB().getId()).isEqualTo("sub-Z");
        assertThat(insert.subA().getId()).isLessThan(insert.subB().getId());
    }

    @Test
    @DisplayName("Invariant 7: Stale cross-question pairs in database are automatically soft-deactivated on calculation")
    void testStaleCrossQuestionPairs_AreSoftDeactivatedOnCalculation() {
        Submission subA1 = Submission.builder().id("sub-A-q1").exam(testExam).question(question1).student(studentA).build();
        Submission subB2 = Submission.builder().id("sub-B-q2").exam(testExam).question(question2).student(studentB).build();

        // Stale legacy pair erroneously formed across questions
        SimilarityPair staleCrossPair = SimilarityPair.builder()
                .id(888L)
                .exam(testExam)
                .submissionA(subA1)
                .submissionB(subB2)
                .isActive(true)
                .build();

        when(examRepository.findById("exam-mq-10")).thenReturn(Optional.of(testExam));
        when(submissionRepository.findByExam_Id("exam-mq-10")).thenReturn(List.of(subA1, subB2));
        when(similarityPairRepository.findByExam_Id("exam-mq-10")).thenReturn(List.of(staleCrossPair));
        when(fingerprintRepository.findBySubmission_Id(anyString())).thenReturn(Collections.emptyList());

        SimilarityMatrixService.MatrixCalculationPlan plan = similarityMatrixService.computeCalculation("exam-mq-10");

        // Stale cross pair is detected as not computed in the partitioned matrix and flagged for deactivation
        assertThat(plan.pairsToDeactivate()).contains(888L);
        assertThat(plan.inserts()).isEmpty();
        assertThat(plan.updates()).isEmpty();

        ExamMatrixCoordination coord = ExamMatrixCoordination.builder()
                .examId("exam-mq-10")
                .lockedGeneration(3L)
                .leaseToken("tok-3")
                .leaseExpiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        when(coordinationRepository.findByExamIdForUpdate("exam-mq-10")).thenReturn(Optional.of(coord));

        similarityMatrixService.commitReconciliation("exam-mq-10", 3L, "tok-3", plan);

        verify(similarityPairRepository).deactivatePairsByIds(List.of(888L));
    }
}

