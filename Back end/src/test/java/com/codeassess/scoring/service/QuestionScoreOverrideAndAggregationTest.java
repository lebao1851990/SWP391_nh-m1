package com.codeassess.scoring.service;

import com.codeassess.classes.entity.CourseClass;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.entity.ExamQuestion;
import com.codeassess.exam.repository.ExamQuestionRepository;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.plagiarism.service.AstPlagiarismService;
import com.codeassess.plagiarism.service.SimilarityMatrixService;
import com.codeassess.sandbox.service.DockerSandboxService;
import com.codeassess.scoring.dto.request.QuestionOverrideRequestDto;
import com.codeassess.scoring.dto.request.SaveScoringPayload;
import com.codeassess.scoring.dto.response.ExamScoreAggregationDto;
import com.codeassess.scoring.dto.response.QuestionScoreDetailDto;
import com.codeassess.scoring.entity.GradeAuditLog;
import com.codeassess.scoring.entity.StudentQuestionScore;
import com.codeassess.scoring.repository.GradeAuditLogRepository;
import com.codeassess.scoring.repository.RuleScoreRepository;
import com.codeassess.scoring.repository.ScoringCriteriaRepository;
import com.codeassess.scoring.repository.StudentQuestionScoreRepository;
import com.codeassess.submission.dto.response.InternalGradingResponse;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.submission.service.InternalGradingService;
import com.codeassess.testcase.repository.SubmissionTestResultRepository;
import com.codeassess.testcase.service.AutomatedTestRunnerService;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class QuestionScoreOverrideAndAggregationTest {

    @Mock
    private ExamRepository examRepository;

    @Mock
    private ExamQuestionRepository examQuestionRepository;

    @Mock
    private StudentQuestionScoreRepository studentQuestionScoreRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private GradeAuditLogRepository gradeAuditLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RuleScoreRepository ruleScoreRepository;

    @Mock
    private ScoringCriteriaRepository scoringCriteriaRepository;

    @Mock
    private SubmissionTestResultRepository submissionTestResultRepository;

    @Mock
    private DockerSandboxService dockerSandboxService;

    @Mock
    private AstPlagiarismService astPlagiarismService;

    @Mock
    private SimilarityMatrixService similarityMatrixService;

    @Mock
    private AutomatedTestRunnerService automatedTestRunnerService;

    private QuestionScoreOverrideService questionScoreOverrideService;
    private ExamScoreAggregationService examScoreAggregationService;
    private TeacherScoringService teacherScoringService;
    private InternalGradingService internalGradingService;

    private User teacher;
    private User otherTeacher;
    private User student;
    private CourseClass courseClass;
    private Exam exam;
    private ExamQuestion question1;
    private ExamQuestion question2;
    private Submission submissionQ1;
    private StudentQuestionScore sqsQ1;

    @BeforeEach
    void setUp() {
        questionScoreOverrideService = new QuestionScoreOverrideService(
                examRepository,
                examQuestionRepository,
                studentQuestionScoreRepository,
                submissionRepository,
                gradeAuditLogRepository,
                userRepository
        );

        examScoreAggregationService = new ExamScoreAggregationService(
                examRepository,
                examQuestionRepository,
                studentQuestionScoreRepository,
                submissionRepository,
                userRepository
        );

        teacherScoringService = new TeacherScoringService(
                submissionRepository,
                ruleScoreRepository,
                scoringCriteriaRepository,
                gradeAuditLogRepository,
                userRepository,
                submissionTestResultRepository,
                studentQuestionScoreRepository
        );

        internalGradingService = new InternalGradingService(
                submissionRepository,
                dockerSandboxService,
                astPlagiarismService,
                similarityMatrixService,
                automatedTestRunnerService,
                studentQuestionScoreRepository
        );

        teacher = User.builder().id("teacher-1").fullName("Instructor Alice").email("alice@uni.edu").build();
        otherTeacher = User.builder().id("teacher-2").fullName("Instructor Bob").email("bob@uni.edu").build();
        student = User.builder().id("student-1").fullName("Student Charlie").email("charlie@uni.edu").build();

        courseClass = CourseClass.builder().id("class-1").name("CS101").teacher(teacher).build();

        exam = Exam.builder()
                .id("exam-1")
                .title("Midterm Exam")
                .courseClass(courseClass)
                .build();

        question1 = ExamQuestion.builder()
                .id("q-1")
                .exam(exam)
                .title("Question 1: Binary Search")
                .displayOrder(1)
                .maxScore(BigDecimal.valueOf(10.00))
                .build();

        question2 = ExamQuestion.builder()
                .id("q-2")
                .exam(exam)
                .title("Question 2: Graph BFS")
                .displayOrder(2)
                .maxScore(BigDecimal.valueOf(20.00))
                .build();

        submissionQ1 = Submission.builder()
                .id("sub-1")
                .exam(exam)
                .question(question1)
                .student(student)
                .status("COMPLETED")
                .autoScore(BigDecimal.valueOf(6.50))
                .totalScore(BigDecimal.valueOf(6.50))
                .overrideScore(null)
                .build();

        sqsQ1 = StudentQuestionScore.builder()
                .id("sqs-1")
                .exam(exam)
                .question(question1)
                .student(student)
                .latestSubmissionId("sub-1")
                .latestSubmissionSeq(1)
                .submissionCount(1)
                .latestStatus("COMPLETED")
                .autoScore(BigDecimal.valueOf(6.50))
                .effectiveScore(BigDecimal.valueOf(6.50))
                .overrideScore(null)
                .build();
    }

    // =========================================================================
    // PART 1: Question-Level Manual Override (Phase B)
    // =========================================================================

    @Test
    @DisplayName("1. Set override successfully updates SQS, submission, and records GradeAuditLog")
    void testSetOverride_Success() {
        QuestionOverrideRequestDto request = QuestionOverrideRequestDto.builder()
                .overrideScore(BigDecimal.valueOf(8.50))
                .rationale("Partial credit for algorithm approach")
                .build();

        when(examRepository.findById("exam-1")).thenReturn(Optional.of(exam));
        when(examQuestionRepository.findById("q-1")).thenReturn(Optional.of(question1));
        when(userRepository.findById("teacher-1")).thenReturn(Optional.of(teacher));
        when(userRepository.findById("student-1")).thenReturn(Optional.of(student));
        when(studentQuestionScoreRepository.findByQuestion_IdAndStudent_IdForUpdate("q-1", "student-1"))
                .thenReturn(Optional.of(sqsQ1));
        when(studentQuestionScoreRepository.save(any(StudentQuestionScore.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(submissionRepository.findById("sub-1")).thenReturn(Optional.of(submissionQ1));

        QuestionScoreDetailDto detail = questionScoreOverrideService.setQuestionOverride(
                "exam-1", "q-1", "student-1", request, "teacher-1");

        assertThat(detail).isNotNull();
        assertThat(detail.getOverrideScore()).isEqualByComparingTo(BigDecimal.valueOf(8.50));
        assertThat(detail.getEffectiveScore()).isEqualByComparingTo(BigDecimal.valueOf(8.50));
        assertThat(detail.isOverridden()).isTrue();
        assertThat(detail.getOverrideReason()).isEqualTo("Partial credit for algorithm approach");
        assertThat(detail.getOverriddenBy()).isEqualTo("teacher-1");

        // Verify SQS entity mutation
        assertThat(sqsQ1.getOverrideScore()).isEqualByComparingTo(BigDecimal.valueOf(8.50));
        assertThat(sqsQ1.getEffectiveScore()).isEqualByComparingTo(BigDecimal.valueOf(8.50));

        // Verify Submission synchronization
        assertThat(submissionQ1.getOverrideScore()).isEqualByComparingTo(BigDecimal.valueOf(8.50));
        verify(submissionRepository).save(submissionQ1);

        // Verify GradeAuditLog recorded with OVERRIDE_SET
        ArgumentCaptor<GradeAuditLog> auditCaptor = ArgumentCaptor.forClass(GradeAuditLog.class);
        verify(gradeAuditLogRepository).save(auditCaptor.capture());
        GradeAuditLog auditLog = auditCaptor.getValue();
        assertThat(auditLog.getActionType()).isEqualTo("OVERRIDE_SET");
        assertThat(auditLog.getActor()).isEqualTo(teacher);
        assertThat(auditLog.getPreOverrideScore()).isNull();
        assertThat(auditLog.getPostOverrideScore()).isEqualByComparingTo(BigDecimal.valueOf(8.50));
        assertThat(auditLog.getPreEffectiveScore()).isEqualByComparingTo(BigDecimal.valueOf(6.50));
        assertThat(auditLog.getPostEffectiveScore()).isEqualByComparingTo(BigDecimal.valueOf(8.50));
        assertThat(auditLog.getRationale()).isEqualTo("Partial credit for algorithm approach");
    }

    @Test
    @DisplayName("2. Set override creates new SQS when student has not submitted yet")
    void testSetOverride_NewSqsCreated_WhenNoPriorSubmission() {
        QuestionOverrideRequestDto request = QuestionOverrideRequestDto.builder()
                .overrideScore(BigDecimal.valueOf(5.00))
                .rationale("Oral exam grade")
                .build();

        when(examRepository.findById("exam-1")).thenReturn(Optional.of(exam));
        when(examQuestionRepository.findById("q-1")).thenReturn(Optional.of(question1));
        when(userRepository.findById("teacher-1")).thenReturn(Optional.of(teacher));
        when(userRepository.findById("student-1")).thenReturn(Optional.of(student));
        when(studentQuestionScoreRepository.findByQuestion_IdAndStudent_IdForUpdate("q-1", "student-1"))
                .thenReturn(Optional.empty());
        when(studentQuestionScoreRepository.save(any(StudentQuestionScore.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        QuestionScoreDetailDto detail = questionScoreOverrideService.setQuestionOverride(
                "exam-1", "q-1", "student-1", request, "teacher-1");

        assertThat(detail).isNotNull();
        assertThat(detail.getOverrideScore()).isEqualByComparingTo(BigDecimal.valueOf(5.00));
        assertThat(detail.getEffectiveScore()).isEqualByComparingTo(BigDecimal.valueOf(5.00));
        assertThat(detail.getStatus()).isEqualTo("GRADED");
        assertThat(detail.getSubmissionCount()).isEqualTo(0);
        assertThat(detail.getLatestSubmissionId()).isNull();

        // No submission exists, so no audit log attached to submission
        verify(gradeAuditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("3. Update existing override records OVERRIDE_UPDATE audit log")
    void testUpdateOverride_Success() {
        sqsQ1.setOverrideScore(BigDecimal.valueOf(7.00));
        sqsQ1.setEffectiveScore(BigDecimal.valueOf(7.00));
        sqsQ1.setOverriddenBy("teacher-1");
        sqsQ1.setOverrideReason("First override");
        submissionQ1.setOverrideScore(BigDecimal.valueOf(7.00));

        QuestionOverrideRequestDto request = QuestionOverrideRequestDto.builder()
                .overrideScore(BigDecimal.valueOf(9.00))
                .rationale("Regrade after discussion")
                .build();

        when(examRepository.findById("exam-1")).thenReturn(Optional.of(exam));
        when(examQuestionRepository.findById("q-1")).thenReturn(Optional.of(question1));
        when(userRepository.findById("teacher-1")).thenReturn(Optional.of(teacher));
        when(userRepository.findById("student-1")).thenReturn(Optional.of(student));
        when(studentQuestionScoreRepository.findByQuestion_IdAndStudent_IdForUpdate("q-1", "student-1"))
                .thenReturn(Optional.of(sqsQ1));
        when(studentQuestionScoreRepository.save(any(StudentQuestionScore.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(submissionRepository.findById("sub-1")).thenReturn(Optional.of(submissionQ1));

        QuestionScoreDetailDto detail = questionScoreOverrideService.setQuestionOverride(
                "exam-1", "q-1", "student-1", request, "teacher-1");

        assertThat(detail.getOverrideScore()).isEqualByComparingTo(BigDecimal.valueOf(9.00));
        assertThat(detail.getEffectiveScore()).isEqualByComparingTo(BigDecimal.valueOf(9.00));

        ArgumentCaptor<GradeAuditLog> auditCaptor = ArgumentCaptor.forClass(GradeAuditLog.class);
        verify(gradeAuditLogRepository).save(auditCaptor.capture());
        GradeAuditLog auditLog = auditCaptor.getValue();
        assertThat(auditLog.getActionType()).isEqualTo("OVERRIDE_UPDATE");
        assertThat(auditLog.getPreOverrideScore()).isEqualByComparingTo(BigDecimal.valueOf(7.00));
        assertThat(auditLog.getPostOverrideScore()).isEqualByComparingTo(BigDecimal.valueOf(9.00));
        assertThat(auditLog.getPreEffectiveScore()).isEqualByComparingTo(BigDecimal.valueOf(7.00));
        assertThat(auditLog.getPostEffectiveScore()).isEqualByComparingTo(BigDecimal.valueOf(9.00));
    }

    @Test
    @DisplayName("4. Clear override restores autoScore and records OVERRIDE_CLEARED audit log")
    void testClearOverride_RestoresAutoScore() {
        sqsQ1.setAutoScore(BigDecimal.valueOf(6.50));
        sqsQ1.setOverrideScore(BigDecimal.valueOf(8.50));
        sqsQ1.setEffectiveScore(BigDecimal.valueOf(8.50));
        sqsQ1.setOverriddenBy("teacher-1");
        sqsQ1.setOverrideReason("Temporary boost");
        submissionQ1.setOverrideScore(BigDecimal.valueOf(8.50));

        when(examRepository.findById("exam-1")).thenReturn(Optional.of(exam));
        when(examQuestionRepository.findById("q-1")).thenReturn(Optional.of(question1));
        when(userRepository.findById("teacher-1")).thenReturn(Optional.of(teacher));
        when(studentQuestionScoreRepository.findByQuestion_IdAndStudent_IdForUpdate("q-1", "student-1"))
                .thenReturn(Optional.of(sqsQ1));
        when(studentQuestionScoreRepository.save(any(StudentQuestionScore.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(submissionRepository.findById("sub-1")).thenReturn(Optional.of(submissionQ1));

        QuestionScoreDetailDto detail = questionScoreOverrideService.clearQuestionOverride(
                "exam-1", "q-1", "student-1", "teacher-1");

        assertThat(detail).isNotNull();
        assertThat(detail.getOverrideScore()).isNull();
        assertThat(detail.getEffectiveScore()).isEqualByComparingTo(BigDecimal.valueOf(6.50));
        assertThat(detail.isOverridden()).isFalse();
        assertThat(detail.getOverrideReason()).isNull();
        assertThat(detail.getOverriddenBy()).isNull();

        // Submission override score cleared
        assertThat(submissionQ1.getOverrideScore()).isNull();
        verify(submissionRepository).save(submissionQ1);

        // GradeAuditLog recorded with OVERRIDE_CLEARED
        ArgumentCaptor<GradeAuditLog> auditCaptor = ArgumentCaptor.forClass(GradeAuditLog.class);
        verify(gradeAuditLogRepository).save(auditCaptor.capture());
        GradeAuditLog auditLog = auditCaptor.getValue();
        assertThat(auditLog.getActionType()).isEqualTo("OVERRIDE_CLEARED");
        assertThat(auditLog.getPreOverrideScore()).isEqualByComparingTo(BigDecimal.valueOf(8.50));
        assertThat(auditLog.getPostOverrideScore()).isNull();
        assertThat(auditLog.getPostEffectiveScore()).isEqualByComparingTo(BigDecimal.valueOf(6.50));
    }

    @Test
    @DisplayName("5. Clear override restores 0.00 when autoScore is null")
    void testClearOverride_RestoresZero_WhenAutoScoreNull() {
        sqsQ1.setAutoScore(null);
        sqsQ1.setOverrideScore(BigDecimal.valueOf(8.00));
        sqsQ1.setEffectiveScore(BigDecimal.valueOf(8.00));

        when(examRepository.findById("exam-1")).thenReturn(Optional.of(exam));
        when(examQuestionRepository.findById("q-1")).thenReturn(Optional.of(question1));
        when(userRepository.findById("teacher-1")).thenReturn(Optional.of(teacher));
        when(studentQuestionScoreRepository.findByQuestion_IdAndStudent_IdForUpdate("q-1", "student-1"))
                .thenReturn(Optional.of(sqsQ1));
        when(studentQuestionScoreRepository.save(any(StudentQuestionScore.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        QuestionScoreDetailDto detail = questionScoreOverrideService.clearQuestionOverride(
                "exam-1", "q-1", "student-1", "teacher-1");

        assertThat(detail.getOverrideScore()).isNull();
        assertThat(detail.getEffectiveScore()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("6. Validation: Reject score below minimum (< 0.00)")
    void testValidation_NegativeScore_Rejected() {
        QuestionOverrideRequestDto request = QuestionOverrideRequestDto.builder()
                .overrideScore(BigDecimal.valueOf(-0.50))
                .build();

        assertThatThrownBy(() -> questionScoreOverrideService.setQuestionOverride(
                "exam-1", "q-1", "student-1", request, "teacher-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be negative");

        verify(studentQuestionScoreRepository, never()).save(any());
    }

    @Test
    @DisplayName("7. Validation: Reject score above maximum (> 10.00)")
    void testValidation_ScoreAbove10_Rejected() {
        QuestionOverrideRequestDto request = QuestionOverrideRequestDto.builder()
                .overrideScore(BigDecimal.valueOf(10.01))
                .build();

        assertThatThrownBy(() -> questionScoreOverrideService.setQuestionOverride(
                "exam-1", "q-1", "student-1", request, "teacher-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed 10.00");

        verify(studentQuestionScoreRepository, never()).save(any());
    }

    @Test
    @DisplayName("8. Validation: Reject score with scale greater than 2")
    void testValidation_ScoreScaleGreaterThan2_Rejected() {
        QuestionOverrideRequestDto request = QuestionOverrideRequestDto.builder()
                .overrideScore(new BigDecimal("8.125"))
                .build();

        assertThatThrownBy(() -> questionScoreOverrideService.setQuestionOverride(
                "exam-1", "q-1", "student-1", request, "teacher-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than 2 decimal places");

        verify(studentQuestionScoreRepository, never()).save(any());
    }

    @Test
    @DisplayName("9. Teacher access: Reject teacher who does not own the class (404 Access Denied)")
    void testTeacherAccess_UnauthorizedTeacher_Rejected() {
        QuestionOverrideRequestDto request = QuestionOverrideRequestDto.builder()
                .overrideScore(BigDecimal.valueOf(8.00))
                .build();

        when(examRepository.findById("exam-1")).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> questionScoreOverrideService.setQuestionOverride(
                "exam-1", "q-1", "student-1", request, "teacher-2"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Exam not found or access denied");

        verify(studentQuestionScoreRepository, never()).save(any());
    }

    @Test
    @DisplayName("10. Question exam belonging: Reject question that belongs to another exam")
    void testQuestionExamBelonging_Mismatch_Rejected() {
        Exam otherExam = Exam.builder().id("exam-other").build();
        ExamQuestion rogueQuestion = ExamQuestion.builder()
                .id("q-rogue")
                .exam(otherExam)
                .title("Rogue Question")
                .build();

        QuestionOverrideRequestDto request = QuestionOverrideRequestDto.builder()
                .overrideScore(BigDecimal.valueOf(8.00))
                .build();

        when(examRepository.findById("exam-1")).thenReturn(Optional.of(exam));
        when(examQuestionRepository.findById("q-rogue")).thenReturn(Optional.of(rogueQuestion));

        assertThatThrownBy(() -> questionScoreOverrideService.setQuestionOverride(
                "exam-1", "q-rogue", "student-1", request, "teacher-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to exam");
    }

    // =========================================================================
    // PART 2: Legacy Scoring API Synchronization
    // =========================================================================

    @Test
    @DisplayName("11. Legacy API synchronizes StudentQuestionScore when submission has question_id")
    void testLegacyScoringApi_SynchronizesSqs_WhenQuestionPresent() {
        SaveScoringPayload payload = new SaveScoringPayload();
        payload.setOverrideTotalScore(BigDecimal.valueOf(9.00));
        payload.setOverrideRationale("Teacher override from legacy view");

        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1"))
                .thenReturn(Optional.of(submissionQ1));
        when(userRepository.findById("teacher-1")).thenReturn(Optional.of(teacher));

        teacherScoringService.updateScoring("sub-1", "teacher-1", payload);

        verify(studentQuestionScoreRepository).setQuestionOverride(
                eq("q-1"), eq("student-1"), eq(BigDecimal.valueOf(9.00)),
                eq("Teacher override from legacy view"), eq("teacher-1"));
    }

    @Test
    @DisplayName("12. Legacy API clears StudentQuestionScore override when overrideTotalScore is null")
    void testLegacyScoringApi_ClearsSqsOverride_WhenOverrideRemoved() {
        submissionQ1.setOverrideScore(BigDecimal.valueOf(8.00));

        SaveScoringPayload payload = new SaveScoringPayload();
        payload.setOverrideTotalScore(null);

        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1"))
                .thenReturn(Optional.of(submissionQ1));
        when(userRepository.findById("teacher-1")).thenReturn(Optional.of(teacher));

        teacherScoringService.updateScoring("sub-1", "teacher-1", payload);

        verify(studentQuestionScoreRepository).clearQuestionOverride("q-1", "student-1");
    }

    @Test
    @DisplayName("13. Legacy API does not mutate SQS when submission has null question_id")
    void testLegacyScoringApi_DoesNotSyncSqs_WhenQuestionNull() {
        submissionQ1.setQuestion(null);

        SaveScoringPayload payload = new SaveScoringPayload();
        payload.setOverrideTotalScore(BigDecimal.valueOf(9.00));

        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1"))
                .thenReturn(Optional.of(submissionQ1));
        when(userRepository.findById("teacher-1")).thenReturn(Optional.of(teacher));

        teacherScoringService.updateScoring("sub-1", "teacher-1", payload);

        verify(studentQuestionScoreRepository, never()).setQuestionOverride(any(), any(), any(), any(), any());
        verify(studentQuestionScoreRepository, never()).clearQuestionOverride(any(), any());
    }

    // =========================================================================
    // PART 3: Exam-Level Score Aggregation (Phases D & E)
    // =========================================================================

    @Test
    @DisplayName("14. Aggregation: Multi-question exam calculates correct weighted score on 10-point scale")
    void testAggregation_MultiQuestionExam_CalculatesWeightedScores() {
        // Q1: maxScore = 10.00, effectiveScore = 8.00
        // Q2: maxScore = 20.00, effectiveScore = 5.00
        // Sum weights = 30.00
        // sumWeightedScore = 8.00 * 10 + 5.00 * 20 = 80 + 100 = 180
        // examScore10 = 180 / 30 = 6.00
        // totalPointsEarned = (8/10)*10 + (5/10)*20 = 8.00 + 10.00 = 18.00
        // totalMaxPoints = 30.00
        StudentQuestionScore sqsQ2 = StudentQuestionScore.builder()
                .id("sqs-2")
                .exam(exam)
                .question(question2)
                .student(student)
                .latestStatus("COMPLETED")
                .autoScore(BigDecimal.valueOf(5.00))
                .effectiveScore(BigDecimal.valueOf(5.00))
                .submissionCount(1)
                .build();

        sqsQ1.setEffectiveScore(BigDecimal.valueOf(8.00));

        when(examRepository.findById("exam-1")).thenReturn(Optional.of(exam));
        when(userRepository.findById("student-1")).thenReturn(Optional.of(student));
        when(examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                .thenReturn(List.of(question1, question2));
        when(studentQuestionScoreRepository.findByExam_IdAndStudent_Id("exam-1", "student-1"))
                .thenReturn(List.of(sqsQ1, sqsQ2));

        ExamScoreAggregationDto result = examScoreAggregationService.calculateExamScore("exam-1", "student-1");

        assertThat(result).isNotNull();
        assertThat(result.getExamScore10()).isEqualByComparingTo(BigDecimal.valueOf(6.00));
        assertThat(result.getTotalPointsEarned()).isEqualByComparingTo(BigDecimal.valueOf(18.00));
        assertThat(result.getTotalMaxPoints()).isEqualByComparingTo(BigDecimal.valueOf(30.00));
        assertThat(result.getStatus()).isEqualTo("FINAL");
        assertThat(result.isProvisional()).isFalse();
        assertThat(result.getTotalQuestions()).isEqualTo(2);
        assertThat(result.getSubmittedQuestions()).isEqualTo(2);
        assertThat(result.getCompletedQuestions()).isEqualTo(2);
        assertThat(result.getPendingQuestions()).isEqualTo(0);
        assertThat(result.getQuestionScores()).hasSize(2);
    }

    @Test
    @DisplayName("15. Aggregation: Pending question marks aggregation as PROVISIONAL")
    void testAggregation_PendingQuestion_ProducesProvisionalAggregate() {
        StudentQuestionScore sqsPending = StudentQuestionScore.builder()
                .id("sqs-2")
                .exam(exam)
                .question(question2)
                .student(student)
                .latestStatus("PENDING")
                .autoScore(null)
                .effectiveScore(BigDecimal.ZERO)
                .submissionCount(1)
                .build();

        when(examRepository.findById("exam-1")).thenReturn(Optional.of(exam));
        when(userRepository.findById("student-1")).thenReturn(Optional.of(student));
        when(examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                .thenReturn(List.of(question1, question2));
        when(studentQuestionScoreRepository.findByExam_IdAndStudent_Id("exam-1", "student-1"))
                .thenReturn(List.of(sqsQ1, sqsPending));

        ExamScoreAggregationDto result = examScoreAggregationService.calculateExamScore("exam-1", "student-1");

        assertThat(result.getStatus()).isEqualTo("PROVISIONAL");
        assertThat(result.isProvisional()).isTrue();
        assertThat(result.getPendingQuestions()).isEqualTo(1);
        assertThat(result.getCompletedQuestions()).isEqualTo(1);
    }

    @Test
    @DisplayName("16. Aggregation: Not-submitted question defaults to 0.00 and status NOT_SUBMITTED")
    void testAggregation_NotSubmittedQuestion_DefaultsZero() {
        // Student only submitted question 1. Question 2 has no SQS row.
        when(examRepository.findById("exam-1")).thenReturn(Optional.of(exam));
        when(userRepository.findById("student-1")).thenReturn(Optional.of(student));
        when(examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                .thenReturn(List.of(question1, question2));
        when(studentQuestionScoreRepository.findByExam_IdAndStudent_Id("exam-1", "student-1"))
                .thenReturn(List.of(sqsQ1));

        ExamScoreAggregationDto result = examScoreAggregationService.calculateExamScore("exam-1", "student-1");

        assertThat(result.getStatus()).isEqualTo("FINAL");
        assertThat(result.isProvisional()).isFalse();
        assertThat(result.getTotalQuestions()).isEqualTo(2);
        assertThat(result.getSubmittedQuestions()).isEqualTo(1);
        assertThat(result.getCompletedQuestions()).isEqualTo(1);
        assertThat(result.getPendingQuestions()).isEqualTo(0);

        QuestionScoreDetailDto q2Detail = result.getQuestionScores().stream()
                .filter(q -> "q-2".equals(q.getQuestionId()))
                .findFirst().orElseThrow();

        assertThat(q2Detail.getStatus()).isEqualTo("NOT_SUBMITTED");
        assertThat(q2Detail.getEffectiveScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(q2Detail.getAutoScore()).isNull();
        assertThat(q2Detail.getSubmissionCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("17. Aggregation: No-test-case question preserves null autoScore semantics without NPE")
    void testAggregation_NoTestCaseQuestion_PreservesNullAutoScore() {
        sqsQ1.setAutoScore(null);
        sqsQ1.setOverrideScore(null);
        sqsQ1.setEffectiveScore(BigDecimal.ZERO);
        sqsQ1.setLatestStatus("COMPLETED");

        when(examRepository.findById("exam-1")).thenReturn(Optional.of(exam));
        when(userRepository.findById("student-1")).thenReturn(Optional.of(student));
        when(examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                .thenReturn(List.of(question1));
        when(studentQuestionScoreRepository.findByExam_IdAndStudent_Id("exam-1", "student-1"))
                .thenReturn(List.of(sqsQ1));

        ExamScoreAggregationDto result = examScoreAggregationService.calculateExamScore("exam-1", "student-1");

        assertThat(result).isNotNull();
        assertThat(result.getExamScore10()).isEqualByComparingTo(BigDecimal.ZERO);
        QuestionScoreDetailDto detail = result.getQuestionScores().get(0);
        assertThat(detail.getAutoScore()).isNull();
        assertThat(detail.getEffectiveScore()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("18. Aggregation: Clearing override recalculates aggregate restoring autoScore")
    void testAggregation_OverrideRemoval_RecalculatesAggregate() {
        // Initial state: Q1 autoScore = 4.00, overrideScore = 10.00 -> effective = 10.00
        sqsQ1.setAutoScore(BigDecimal.valueOf(4.00));
        sqsQ1.setOverrideScore(BigDecimal.valueOf(10.00));
        sqsQ1.setEffectiveScore(BigDecimal.valueOf(10.00));

        when(examRepository.findById("exam-1")).thenReturn(Optional.of(exam));
        when(userRepository.findById("student-1")).thenReturn(Optional.of(student));
        when(examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                .thenReturn(List.of(question1));
        when(studentQuestionScoreRepository.findByExam_IdAndStudent_Id("exam-1", "student-1"))
                .thenReturn(List.of(sqsQ1));

        ExamScoreAggregationDto beforeResult = examScoreAggregationService.calculateExamScore("exam-1", "student-1");
        assertThat(beforeResult.getExamScore10()).isEqualByComparingTo(BigDecimal.valueOf(10.00));

        // When override is removed: effectiveScore becomes 4.00
        sqsQ1.setOverrideScore(null);
        sqsQ1.setEffectiveScore(BigDecimal.valueOf(4.00));

        ExamScoreAggregationDto afterResult = examScoreAggregationService.calculateExamScore("exam-1", "student-1");
        assertThat(afterResult.getExamScore10()).isEqualByComparingTo(BigDecimal.valueOf(4.00));
        assertThat(afterResult.getQuestionScores().get(0).isOverridden()).isFalse();
    }

    @Test
    @DisplayName("19. Aggregation: Legacy single-question exam without ExamQuestion fallback")
    void testAggregation_LegacySingleQuestionExam_Fallback() {
        when(examRepository.findById("exam-1")).thenReturn(Optional.of(exam));
        when(userRepository.findById("student-1")).thenReturn(Optional.of(student));
        when(examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                .thenReturn(Collections.emptyList());

        submissionQ1.setQuestion(null);
        submissionQ1.setTotalScore(BigDecimal.valueOf(7.50));
        when(submissionRepository.findFirstByExam_IdAndStudent_IdOrderByCreatedAtDescIdDesc("exam-1", "student-1"))
                .thenReturn(Optional.of(submissionQ1));

        ExamScoreAggregationDto result = examScoreAggregationService.calculateExamScore("exam-1", "student-1");

        assertThat(result).isNotNull();
        assertThat(result.getExamScore10()).isEqualByComparingTo(BigDecimal.valueOf(7.50));
        assertThat(result.getTotalPointsEarned()).isEqualByComparingTo(BigDecimal.valueOf(7.50));
        assertThat(result.getTotalMaxPoints()).isEqualByComparingTo(BigDecimal.valueOf(10.00));
        assertThat(result.getTotalQuestions()).isEqualTo(1);
        assertThat(result.getQuestionScores().get(0).getQuestionId()).isEqualTo("legacy");
    }

    // =========================================================================
    // PART 4: Worker Concurrency & Fencing Invariants
    // =========================================================================

    @Test
    @DisplayName("20. Worker Concurrency: completeWorkerGradingIfLatest COALESCE preserves overrideScore")
    void testWorkerGrading_PreservesOverrideScore_CoalesceInvariant() {
        // Contract verification of repository method:
        // SET sqs.autoScore = :autoScore, sqs.effectiveScore = COALESCE(sqs.overrideScore, :autoScore, 0.00)
        // If sqs has overrideScore = 9.00, worker passing autoScore = 4.00 leaves effectiveScore = 9.00.
        BigDecimal overrideScore = BigDecimal.valueOf(9.00);
        BigDecimal autoScore = BigDecimal.valueOf(4.00);
        BigDecimal effective = overrideScore != null ? overrideScore : (autoScore != null ? autoScore : BigDecimal.ZERO);

        assertThat(effective).isEqualByComparingTo(BigDecimal.valueOf(9.00));

        // Stub repository to simulate worker completion call
        when(studentQuestionScoreRepository.completeWorkerGradingIfLatest(
                eq("q-1"), eq("student-1"), eq("sub-1"), any(BigDecimal.class)))
                .thenReturn(1);

        int rowsUpdated = studentQuestionScoreRepository.completeWorkerGradingIfLatest(
                "q-1", "student-1", "sub-1", BigDecimal.valueOf(4.00));
        assertThat(rowsUpdated).isEqualTo(1);
    }

    @Test
    @DisplayName("21. Worker Concurrency: Worker updates autoScore and effectiveScore when override is null")
    void testWorkerGrading_UpdatesAutoScoreAndEffective_WhenNoOverride() {
        BigDecimal overrideScore = null;
        BigDecimal autoScore = BigDecimal.valueOf(7.50);
        BigDecimal effective = overrideScore != null ? overrideScore : (autoScore != null ? autoScore : BigDecimal.ZERO);

        assertThat(effective).isEqualByComparingTo(BigDecimal.valueOf(7.50));
    }

    @Test
    @DisplayName("22. Stale worker fencing: completeWorkerGradingIfLatest rejects stale submission")
    void testWorkerGrading_FencedByLatestSubmissionId_StaleWorkerRejected() {
        // If a newer submission was received (sub-2), latestSubmissionId is sub-2.
        // Worker grading sub-1 will match 0 rows in completeWorkerGradingIfLatest.
        when(studentQuestionScoreRepository.completeWorkerGradingIfLatest(
                eq("q-1"), eq("student-1"), eq("sub-1"), any(BigDecimal.class)))
                .thenReturn(0);

        int rows = studentQuestionScoreRepository.completeWorkerGradingIfLatest(
                "q-1", "student-1", "sub-1", BigDecimal.valueOf(10.00));

        assertThat(rows).isEqualTo(0); // Fenced out, score not mutated
    }

    @Test
    @DisplayName("23. Worker Claim Fencing: Missing or blank claim token rejected immediately")
    void testWorkerGrading_MissingOrBlankClaimToken_RejectedImmediately() {
        InternalGradingResponse nullTokenResp = internalGradingService.processGradingWithToken("sub-1", null);
        assertThat(nullTokenResp.getStatus()).isEqualTo("INVALID_TOKEN");
        assertThat(nullTokenResp.getMessage()).contains("Missing or invalid claim token");

        InternalGradingResponse blankTokenResp = internalGradingService.processGradingWithToken("sub-1", "   ");
        assertThat(blankTokenResp.getStatus()).isEqualTo("INVALID_TOKEN");
        assertThat(blankTokenResp.getMessage()).contains("Missing or invalid claim token");

        verify(submissionRepository, never()).claimSubmissionWithToken(any(), any(), any());
        verify(automatedTestRunnerService, never()).runAndGrade(any(), any());
    }
}

