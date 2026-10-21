package com.codeassess.scoring.service;

import com.codeassess.classes.entity.CourseClass;
import com.codeassess.exam.entity.Exam;
import com.codeassess.scoring.entity.ScoringCriteria;
import com.codeassess.scoring.repository.RuleScoreRepository;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StudentScoringServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private RuleScoreRepository ruleScoreRepository;

    @Mock
    private com.codeassess.scoring.repository.GradeAuditLogRepository gradeAuditLogRepository;

    @InjectMocks
    private StudentScoringService studentScoringService;

    private Submission submission;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        User student = User.builder().id("student-1").build();
        CourseClass courseClass = CourseClass.builder().name("Math 101").build();
        Exam exam = Exam.builder().title("Midterm").courseClass(courseClass).build();
        
        submission = Submission.builder().id("sub-1").student(student).exam(exam).build();
    }

    @Test
    void testEffectiveScoreCalculationWithOverride() {
        submission.setTotalScore(BigDecimal.valueOf(18));
        submission.setOverrideScore(BigDecimal.valueOf(20));

        Mockito.when(submissionRepository.findByIdAndStudent_Id("sub-1", "student-1")).thenReturn(Optional.of(submission));
        Mockito.when(ruleScoreRepository.findBySubmission_Id("sub-1")).thenReturn(new ArrayList<>());

        var result = studentScoringService.getStudentResult("sub-1", "student-1");

        assertEquals(BigDecimal.valueOf(18), result.getTotalScore());
        assertEquals(BigDecimal.valueOf(20), result.getOverrideTotalScore());
        assertEquals(BigDecimal.valueOf(20), result.getEffectiveScore());
    }

    @Test
    void testEffectiveScoreCalculationWithoutOverride() {
        submission.setTotalScore(BigDecimal.valueOf(18));
        submission.setOverrideScore(null);

        Mockito.when(submissionRepository.findByIdAndStudent_Id("sub-1", "student-1")).thenReturn(Optional.of(submission));
        Mockito.when(ruleScoreRepository.findBySubmission_Id("sub-1")).thenReturn(new ArrayList<>());

        var result = studentScoringService.getStudentResult("sub-1", "student-1");

        assertEquals(BigDecimal.valueOf(18), result.getTotalScore());
        assertEquals(null, result.getOverrideTotalScore());
        assertEquals(BigDecimal.valueOf(18), result.getEffectiveScore());
    }

    @Test
    void testExtractStudentFeedback_OnlyOverrideRationale_ReturnsNull() {
        String feedback = studentScoringService.extractStudentFeedback("Override Rationale: Confidential note: suspected collusion");
        org.junit.jupiter.api.Assertions.assertNull(feedback, "Override rationale must never be returned as student feedback");
    }

    @Test
    void testExtractStudentFeedback_LegacyUnidentifiedRationale_ReturnsNull() {
        // Under security policy, unidentified text without public comment header must never be treated as public feedback
        String feedback = studentScoringService.extractStudentFeedback("Confidential internal note without tag");
        org.junit.jupiter.api.Assertions.assertNull(feedback, "Unidentified legacy rationale must never leak to student");
    }

    @Test
    void testExtractStudentFeedback_OnlyInstructorComments_ReturnsComments() {
        String feedback = studentScoringService.extractStudentFeedback("Instructor Comments: Great effort on graph traversal");
        assertEquals("Great effort on graph traversal", feedback);
    }

    @Test
    void testExtractStudentFeedback_BothPresent_ReturnsOnlyCommentsWithoutOverride() {
        String feedback = studentScoringService.extractStudentFeedback(
                "Override Rationale: Adjusted for partial credit\nInstructor Comments: Review edge case handling in tests"
        );
        assertEquals("Review edge case handling in tests", feedback);
        org.junit.jupiter.api.Assertions.assertFalse(feedback.contains("Adjusted for partial credit"));
    }

    @Test
    void testExtractStudentFeedback_SpoofedLabelsInOverride_DoesNotLeak() {
        // Single-line spoof attempt inside override rationale
        String feedback1 = studentScoringService.extractStudentFeedback(
                "Override Rationale: Cheating suspected. Instructor Comments: fake note"
        );
        org.junit.jupiter.api.Assertions.assertNull(feedback1, "Spoofed label inside override must not leak note");

        // Multiline escaped spoof attempt with genuine comments
        String feedback2 = studentScoringService.extractStudentFeedback(
                "Override Rationale: Suspicious code\n[Instructor Comments]: fake\nInstructor Comments: Real public feedback"
        );
        assertEquals("Real public feedback", feedback2);
    }

    @Test
    void testExtractStudentFeedback_NullEmptyWhitespace_ReturnsNull() {
        org.junit.jupiter.api.Assertions.assertNull(studentScoringService.extractStudentFeedback(null));
        org.junit.jupiter.api.Assertions.assertNull(studentScoringService.extractStudentFeedback(""));
        org.junit.jupiter.api.Assertions.assertNull(studentScoringService.extractStudentFeedback("   "));
        org.junit.jupiter.api.Assertions.assertNull(studentScoringService.extractStudentFeedback("Instructor Comments:    "));
        org.junit.jupiter.api.Assertions.assertNull(studentScoringService.extractStudentFeedback("Override Rationale:    "));
    }

    @Test
    void testGetStudentResult_MultipleAuditLogs_ReturnsLatestPublicFeedbackAndHidesOverride() {
        submission.setStatus("GRADED");
        submission.setTotalScore(BigDecimal.valueOf(18));

        com.codeassess.scoring.entity.GradeAuditLog log1Latest = com.codeassess.scoring.entity.GradeAuditLog.builder()
                .id("log-2")
                .actionType("OVERRIDE_SET")
                .rationale("Override Rationale: Internal grading penalty applied")
                .build();

        com.codeassess.scoring.entity.GradeAuditLog log2Earlier = com.codeassess.scoring.entity.GradeAuditLog.builder()
                .id("log-1")
                .actionType("INITIAL_GRADING")
                .rationale("Instructor Comments: Well structured solution")
                .build();

        Mockito.when(submissionRepository.findByIdAndStudent_Id("sub-1", "student-1")).thenReturn(Optional.of(submission));
        Mockito.when(ruleScoreRepository.findBySubmission_Id("sub-1")).thenReturn(new ArrayList<>());
        Mockito.when(gradeAuditLogRepository.findBySubmission_IdOrderByCreatedAtDesc("sub-1"))
                .thenReturn(java.util.List.of(log1Latest, log2Earlier));

        var result = studentScoringService.getStudentResult("sub-1", "student-1");

        assertEquals("Well structured solution", result.getInstructorEvaluationComments());
        assertEquals("Well structured solution", result.getInstructorFeedback());
        org.junit.jupiter.api.Assertions.assertFalse(
                String.valueOf(result.getInstructorEvaluationComments()).contains("Internal grading penalty applied")
        );
    }

    @Test
    void testGetStudentResult_OnlyOverrideInAuditLog_ReturnsNullFeedback() {
        submission.setStatus("GRADED");
        submission.setTotalScore(BigDecimal.valueOf(18));

        com.codeassess.scoring.entity.GradeAuditLog logOnlyOverride = com.codeassess.scoring.entity.GradeAuditLog.builder()
                .id("log-1")
                .actionType("OVERRIDE_SET")
                .rationale("Override Rationale: Adjusted score after review")
                .build();

        Mockito.when(submissionRepository.findByIdAndStudent_Id("sub-1", "student-1")).thenReturn(Optional.of(submission));
        Mockito.when(ruleScoreRepository.findBySubmission_Id("sub-1")).thenReturn(new ArrayList<>());
        Mockito.when(gradeAuditLogRepository.findBySubmission_IdOrderByCreatedAtDesc("sub-1"))
                .thenReturn(java.util.List.of(logOnlyOverride));

        var result = studentScoringService.getStudentResult("sub-1", "student-1");

        org.junit.jupiter.api.Assertions.assertNull(result.getInstructorEvaluationComments());
        org.junit.jupiter.api.Assertions.assertNull(result.getInstructorFeedback());
    }

    @Test
    void testGetStudentResult_MultipleLogsWithComments_ReturnsLatestPublicComments() {
        submission.setStatus("GRADED");
        submission.setTotalScore(BigDecimal.valueOf(18));

        com.codeassess.scoring.entity.GradeAuditLog log1Latest = com.codeassess.scoring.entity.GradeAuditLog.builder()
                .id("log-2")
                .actionType("RUBRIC_UPDATE")
                .rationale("Instructor Comments: Latest revised feedback on optimization")
                .build();

        com.codeassess.scoring.entity.GradeAuditLog log2Earlier = com.codeassess.scoring.entity.GradeAuditLog.builder()
                .id("log-1")
                .actionType("INITIAL_GRADING")
                .rationale("Instructor Comments: Old initial feedback")
                .build();

        Mockito.when(submissionRepository.findByIdAndStudent_Id("sub-1", "student-1")).thenReturn(Optional.of(submission));
        Mockito.when(ruleScoreRepository.findBySubmission_Id("sub-1")).thenReturn(new ArrayList<>());
        Mockito.when(gradeAuditLogRepository.findBySubmission_IdOrderByCreatedAtDesc("sub-1"))
                .thenReturn(java.util.List.of(log1Latest, log2Earlier));

        var result = studentScoringService.getStudentResult("sub-1", "student-1");

        assertEquals("Latest revised feedback on optimization", result.getInstructorEvaluationComments());
        assertEquals("Latest revised feedback on optimization", result.getInstructorFeedback());
    }

    @Test
    void testGetStudentResult_LatestHasBoth_ReturnsLatestCommentsWithoutOverride() {
        submission.setStatus("GRADED");
        submission.setTotalScore(BigDecimal.valueOf(18));

        com.codeassess.scoring.entity.GradeAuditLog log = com.codeassess.scoring.entity.GradeAuditLog.builder()
                .id("log-1")
                .actionType("OVERRIDE_SET")
                .rationale("Override Rationale: Adjusted for medical note\nInstructor Comments: Excellent performance on final problem")
                .build();

        Mockito.when(submissionRepository.findByIdAndStudent_Id("sub-1", "student-1")).thenReturn(Optional.of(submission));
        Mockito.when(ruleScoreRepository.findBySubmission_Id("sub-1")).thenReturn(new ArrayList<>());
        Mockito.when(gradeAuditLogRepository.findBySubmission_IdOrderByCreatedAtDesc("sub-1"))
                .thenReturn(java.util.List.of(log));

        var result = studentScoringService.getStudentResult("sub-1", "student-1");

        assertEquals("Excellent performance on final problem", result.getInstructorEvaluationComments());
        assertEquals("Excellent performance on final problem", result.getInstructorFeedback());
        org.junit.jupiter.api.Assertions.assertFalse(
                String.valueOf(result.getInstructorEvaluationComments()).contains("Adjusted for medical note")
        );
    }

    @Test
    void testGetStudentResult_DifferentStudent_ThrowsAccessDenied() {
        Mockito.when(submissionRepository.findByIdAndStudent_Id("sub-1", "other-student"))
                .thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                com.codeassess.common.exception.ResourceNotFoundException.class,
                () -> studentScoringService.getStudentResult("sub-1", "other-student")
        );
    }
}
