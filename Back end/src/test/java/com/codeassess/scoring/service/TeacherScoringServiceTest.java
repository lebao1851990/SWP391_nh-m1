package com.codeassess.scoring.service;

import com.codeassess.classes.entity.CourseClass;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.scoring.dto.request.SaveScoringPayload;
import com.codeassess.scoring.dto.response.GradeAuditLogDto;
import com.codeassess.scoring.entity.GradeAuditLog;
import com.codeassess.scoring.entity.RuleScore;
import com.codeassess.scoring.entity.ScoringCriteria;
import com.codeassess.scoring.repository.GradeAuditLogRepository;
import com.codeassess.scoring.repository.RuleScoreRepository;
import com.codeassess.scoring.repository.ScoringCriteriaRepository;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TeacherScoringServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private RuleScoreRepository ruleScoreRepository;

    @Mock
    private ScoringCriteriaRepository scoringCriteriaRepository;

    @Mock
    private GradeAuditLogRepository gradeAuditLogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TeacherScoringService teacherScoringService;

    private User teacher;
    private User student;
    private Submission submission;
    private ScoringCriteria criteria;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        teacher = User.builder().id("teacher-1").fullName("Teacher One").email("teacher1@test.com").build();
        student = User.builder().id("student-1").fullName("Student One").build();
        CourseClass courseClass = CourseClass.builder().teacher(teacher).name("Class 101").build();
        Exam exam = Exam.builder().courseClass(courseClass).title("Final Exam").build();
        
        submission = Submission.builder()
                .id("sub-1")
                .student(student)
                .exam(exam)
                .status("PENDING")
                .evaluatedAt(null)
                .totalScore(null)
                .overrideScore(null)
                .build();
        
        criteria = ScoringCriteria.builder().id("crit-1").name("Logic").maxScore(BigDecimal.TEN).build();

        when(userRepository.findById("teacher-1")).thenReturn(Optional.of(teacher));
    }

    @Test
    void testNegativeScoreRejected() {
        SaveScoringPayload payload = new SaveScoringPayload();
        SaveScoringPayload.CriteriaScoreRequest csRequest = new SaveScoringPayload.CriteriaScoreRequest();
        csRequest.setCriterionId("crit-1");
        csRequest.setScore(BigDecimal.valueOf(-1));
        payload.setCriteriaScores(List.of(csRequest));

        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1")).thenReturn(Optional.of(submission));
        when(scoringCriteriaRepository.findById("crit-1")).thenReturn(Optional.of(criteria));

        assertThrows(IllegalArgumentException.class, () -> teacherScoringService.updateScoring("sub-1", "teacher-1", payload));
        verify(gradeAuditLogRepository, never()).save(any());
    }

    @Test
    void testScoreGreaterThanMaxRejected() {
        SaveScoringPayload payload = new SaveScoringPayload();
        SaveScoringPayload.CriteriaScoreRequest csRequest = new SaveScoringPayload.CriteriaScoreRequest();
        csRequest.setCriterionId("crit-1");
        csRequest.setScore(BigDecimal.valueOf(11));
        payload.setCriteriaScores(List.of(csRequest));

        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1")).thenReturn(Optional.of(submission));
        when(scoringCriteriaRepository.findById("crit-1")).thenReturn(Optional.of(criteria));

        assertThrows(IllegalArgumentException.class, () -> teacherScoringService.updateScoring("sub-1", "teacher-1", payload));
        verify(gradeAuditLogRepository, never()).save(any());
    }

    @Test
    void testInitialGradingCreatesAuditLog() {
        SaveScoringPayload payload = new SaveScoringPayload();
        SaveScoringPayload.CriteriaScoreRequest csRequest = new SaveScoringPayload.CriteriaScoreRequest();
        csRequest.setCriterionId("crit-1");
        csRequest.setScore(BigDecimal.valueOf(8));
        payload.setCriteriaScores(List.of(csRequest));
        payload.setOverrideRationale("Initial grading rationale");

        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1")).thenReturn(Optional.of(submission));
        when(scoringCriteriaRepository.findById("crit-1")).thenReturn(Optional.of(criteria));
        when(ruleScoreRepository.findBySubmission_Id("sub-1")).thenReturn(new ArrayList<>());

        var result = teacherScoringService.updateScoring("sub-1", "teacher-1", payload);

        assertEquals(BigDecimal.valueOf(8), submission.getTotalScore());
        assertEquals(BigDecimal.valueOf(8), result.getEffectiveScore());
        assertEquals("GRADED", submission.getStatus());

        ArgumentCaptor<GradeAuditLog> logCaptor = ArgumentCaptor.forClass(GradeAuditLog.class);
        verify(gradeAuditLogRepository).save(logCaptor.capture());
        GradeAuditLog log = logCaptor.getValue();

        assertEquals("INITIAL_GRADING", log.getActionType());
        assertNull(log.getPreRubricScore());
        assertEquals(BigDecimal.valueOf(8), log.getPostRubricScore());
        assertNull(log.getPreOverrideScore());
        assertNull(log.getPostOverrideScore());
        assertNull(log.getPreEffectiveScore());
        assertEquals(BigDecimal.valueOf(8), log.getPostEffectiveScore());
        assertEquals("Initial grading rationale", log.getRationale());
        assertEquals(teacher, log.getActor());
    }

    @Test
    void testRubricUpdateCreatesAuditLog() {
        submission.setEvaluatedAt(LocalDateTime.now().minusDays(1));
        submission.setStatus("GRADED");
        submission.setTotalScore(BigDecimal.valueOf(8));

        SaveScoringPayload payload = new SaveScoringPayload();
        SaveScoringPayload.CriteriaScoreRequest csRequest = new SaveScoringPayload.CriteriaScoreRequest();
        csRequest.setCriterionId("crit-1");
        csRequest.setScore(BigDecimal.valueOf(9));
        payload.setCriteriaScores(List.of(csRequest));
        payload.setOverrideRationale("Re-evaluated criterion score");

        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1")).thenReturn(Optional.of(submission));
        when(scoringCriteriaRepository.findById("crit-1")).thenReturn(Optional.of(criteria));
        when(ruleScoreRepository.findBySubmission_Id("sub-1")).thenReturn(new ArrayList<>());

        var result = teacherScoringService.updateScoring("sub-1", "teacher-1", payload);

        assertEquals(BigDecimal.valueOf(9), submission.getTotalScore());
        assertEquals(BigDecimal.valueOf(9), result.getEffectiveScore());

        ArgumentCaptor<GradeAuditLog> logCaptor = ArgumentCaptor.forClass(GradeAuditLog.class);
        verify(gradeAuditLogRepository).save(logCaptor.capture());
        GradeAuditLog log = logCaptor.getValue();

        assertEquals("RUBRIC_UPDATE", log.getActionType());
        assertEquals(BigDecimal.valueOf(8), log.getPreRubricScore());
        assertEquals(BigDecimal.valueOf(9), log.getPostRubricScore());
        assertNull(log.getPreOverrideScore());
        assertNull(log.getPostOverrideScore());
        assertEquals(BigDecimal.valueOf(8), log.getPreEffectiveScore());
        assertEquals(BigDecimal.valueOf(9), log.getPostEffectiveScore());
    }

    @Test
    void testOverrideSetCreatesAuditLog() {
        submission.setEvaluatedAt(LocalDateTime.now().minusDays(1));
        submission.setStatus("GRADED");
        submission.setTotalScore(BigDecimal.valueOf(8));
        submission.setOverrideScore(null);

        SaveScoringPayload payload = new SaveScoringPayload();
        SaveScoringPayload.CriteriaScoreRequest csRequest = new SaveScoringPayload.CriteriaScoreRequest();
        csRequest.setCriterionId("crit-1");
        csRequest.setScore(BigDecimal.valueOf(8));
        payload.setCriteriaScores(List.of(csRequest));
        payload.setOverrideTotalScore(BigDecimal.valueOf(7));
        payload.setOverrideRationale("Penalty for late submission");

        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1")).thenReturn(Optional.of(submission));
        when(scoringCriteriaRepository.findById("crit-1")).thenReturn(Optional.of(criteria));
        when(ruleScoreRepository.findBySubmission_Id("sub-1")).thenReturn(new ArrayList<>());

        var result = teacherScoringService.updateScoring("sub-1", "teacher-1", payload);

        assertEquals(BigDecimal.valueOf(8), submission.getTotalScore());
        assertEquals(BigDecimal.valueOf(7), submission.getOverrideScore());
        assertEquals(BigDecimal.valueOf(7), result.getEffectiveScore());

        ArgumentCaptor<GradeAuditLog> logCaptor = ArgumentCaptor.forClass(GradeAuditLog.class);
        verify(gradeAuditLogRepository).save(logCaptor.capture());
        GradeAuditLog log = logCaptor.getValue();

        assertEquals("OVERRIDE_SET", log.getActionType());
        assertEquals(BigDecimal.valueOf(8), log.getPreRubricScore());
        assertEquals(BigDecimal.valueOf(8), log.getPostRubricScore());
        assertNull(log.getPreOverrideScore());
        assertEquals(BigDecimal.valueOf(7), log.getPostOverrideScore());
        assertEquals(BigDecimal.valueOf(8), log.getPreEffectiveScore());
        assertEquals(BigDecimal.valueOf(7), log.getPostEffectiveScore());
        assertEquals("Penalty for late submission", log.getRationale());
    }

    @Test
    void testOverrideClearedReturnsToRubricScoreAndLogs() {
        submission.setEvaluatedAt(LocalDateTime.now().minusDays(1));
        submission.setStatus("GRADED");
        submission.setTotalScore(BigDecimal.valueOf(8));
        submission.setOverrideScore(BigDecimal.valueOf(6));

        SaveScoringPayload payload = new SaveScoringPayload();
        SaveScoringPayload.CriteriaScoreRequest csRequest = new SaveScoringPayload.CriteriaScoreRequest();
        csRequest.setCriterionId("crit-1");
        csRequest.setScore(BigDecimal.valueOf(8));
        payload.setCriteriaScores(List.of(csRequest));
        payload.setOverrideTotalScore(null); // Clear override
        payload.setOverrideRationale("Removed override; reverting to rubric score");

        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1")).thenReturn(Optional.of(submission));
        when(scoringCriteriaRepository.findById("crit-1")).thenReturn(Optional.of(criteria));
        when(ruleScoreRepository.findBySubmission_Id("sub-1")).thenReturn(new ArrayList<>());

        var result = teacherScoringService.updateScoring("sub-1", "teacher-1", payload);

        assertEquals(BigDecimal.valueOf(8), submission.getTotalScore());
        assertNull(submission.getOverrideScore());
        assertEquals(BigDecimal.valueOf(8), result.getEffectiveScore());

        ArgumentCaptor<GradeAuditLog> logCaptor = ArgumentCaptor.forClass(GradeAuditLog.class);
        verify(gradeAuditLogRepository).save(logCaptor.capture());
        GradeAuditLog log = logCaptor.getValue();

        assertEquals("OVERRIDE_CLEARED", log.getActionType());
        assertEquals(BigDecimal.valueOf(8), log.getPreRubricScore());
        assertEquals(BigDecimal.valueOf(8), log.getPostRubricScore());
        assertEquals(BigDecimal.valueOf(6), log.getPreOverrideScore());
        assertNull(log.getPostOverrideScore());
        assertEquals(BigDecimal.valueOf(6), log.getPreEffectiveScore());
        assertEquals(BigDecimal.valueOf(8), log.getPostEffectiveScore());
    }

    @Test
    void testOverrideUpdateCreatesAuditLog() {
        submission.setEvaluatedAt(LocalDateTime.now().minusDays(1));
        submission.setStatus("GRADED");
        submission.setTotalScore(BigDecimal.valueOf(8));
        submission.setOverrideScore(BigDecimal.valueOf(6));

        SaveScoringPayload payload = new SaveScoringPayload();
        SaveScoringPayload.CriteriaScoreRequest csRequest = new SaveScoringPayload.CriteriaScoreRequest();
        csRequest.setCriterionId("crit-1");
        csRequest.setScore(BigDecimal.valueOf(8));
        payload.setCriteriaScores(List.of(csRequest));
        payload.setOverrideTotalScore(BigDecimal.valueOf(7));
        payload.setOverrideRationale("Adjusted bonus points");

        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1")).thenReturn(Optional.of(submission));
        when(scoringCriteriaRepository.findById("crit-1")).thenReturn(Optional.of(criteria));
        when(ruleScoreRepository.findBySubmission_Id("sub-1")).thenReturn(new ArrayList<>());

        teacherScoringService.updateScoring("sub-1", "teacher-1", payload);

        ArgumentCaptor<GradeAuditLog> logCaptor = ArgumentCaptor.forClass(GradeAuditLog.class);
        verify(gradeAuditLogRepository).save(logCaptor.capture());
        GradeAuditLog log = logCaptor.getValue();

        assertEquals("OVERRIDE_UPDATE", log.getActionType());
        assertEquals(BigDecimal.valueOf(6), log.getPreOverrideScore());
        assertEquals(BigDecimal.valueOf(7), log.getPostOverrideScore());
    }

    @Test
    void testNegativeOverrideScoreRejected() {
        SaveScoringPayload payload = new SaveScoringPayload();
        SaveScoringPayload.CriteriaScoreRequest csRequest = new SaveScoringPayload.CriteriaScoreRequest();
        csRequest.setCriterionId("crit-1");
        csRequest.setScore(BigDecimal.valueOf(8));
        payload.setCriteriaScores(List.of(csRequest));
        payload.setOverrideTotalScore(BigDecimal.valueOf(-2));

        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1")).thenReturn(Optional.of(submission));
        when(scoringCriteriaRepository.findById("crit-1")).thenReturn(Optional.of(criteria));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
                () -> teacherScoringService.updateScoring("sub-1", "teacher-1", payload));
        assertEquals("Override score cannot be negative", ex.getMessage());
        verify(gradeAuditLogRepository, never()).save(any());
    }

    @Test
    void testOverrideScoreExceedingScaleRejected() {
        SaveScoringPayload payload = new SaveScoringPayload();
        SaveScoringPayload.CriteriaScoreRequest csRequest = new SaveScoringPayload.CriteriaScoreRequest();
        csRequest.setCriterionId("crit-1");
        csRequest.setScore(BigDecimal.valueOf(8));
        payload.setCriteriaScores(List.of(csRequest));
        payload.setOverrideTotalScore(BigDecimal.valueOf(15)); // Criteria max is 10

        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1")).thenReturn(Optional.of(submission));
        when(scoringCriteriaRepository.findById("crit-1")).thenReturn(Optional.of(criteria));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
                () -> teacherScoringService.updateScoring("sub-1", "teacher-1", payload));
        assertEquals("Override score cannot exceed maximum assessment score of 10", ex.getMessage());
        verify(gradeAuditLogRepository, never()).save(any());
    }

    @Test
    void testOtherClassTeacherCannotAccessScoringOrAuditLogs() {
        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "other-teacher"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teacherScoringService.getScoringDetail("sub-1", "other-teacher"));
        assertThrows(ResourceNotFoundException.class, () -> teacherScoringService.getAuditLogs("sub-1", "other-teacher"));
        
        SaveScoringPayload payload = new SaveScoringPayload();
        assertThrows(ResourceNotFoundException.class, () -> teacherScoringService.updateScoring("sub-1", "other-teacher", payload));
        verify(gradeAuditLogRepository, never()).save(any());
    }

    @Test
    void testAuditLogActorMatchesAuthenticatedTeacherNotForged() {
        SaveScoringPayload payload = new SaveScoringPayload();
        SaveScoringPayload.CriteriaScoreRequest csRequest = new SaveScoringPayload.CriteriaScoreRequest();
        csRequest.setCriterionId("crit-1");
        csRequest.setScore(BigDecimal.valueOf(8));
        payload.setCriteriaScores(List.of(csRequest));
        payload.setOverrideRationale("Valid reason");

        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1")).thenReturn(Optional.of(submission));
        when(scoringCriteriaRepository.findById("crit-1")).thenReturn(Optional.of(criteria));
        when(ruleScoreRepository.findBySubmission_Id("sub-1")).thenReturn(new ArrayList<>());

        teacherScoringService.updateScoring("sub-1", "teacher-1", payload);

        ArgumentCaptor<GradeAuditLog> logCaptor = ArgumentCaptor.forClass(GradeAuditLog.class);
        verify(gradeAuditLogRepository).save(logCaptor.capture());
        GradeAuditLog savedLog = logCaptor.getValue();

        assertNotNull(savedLog.getActor());
        assertEquals("teacher-1", savedLog.getActor().getId());
        assertEquals("Teacher One", savedLog.getActor().getFullName());
    }

    @Test
    void testGetAuditLogsReturnsOrderedList() {
        GradeAuditLog log1 = GradeAuditLog.builder()
                .id("log-1")
                .submission(submission)
                .actor(teacher)
                .actionType("INITIAL_GRADING")
                .preEffectiveScore(null)
                .postEffectiveScore(BigDecimal.valueOf(8))
                .createdAt(LocalDateTime.now().minusHours(2))
                .build();

        GradeAuditLog log2 = GradeAuditLog.builder()
                .id("log-2")
                .submission(submission)
                .actor(teacher)
                .actionType("OVERRIDE_SET")
                .preEffectiveScore(BigDecimal.valueOf(8))
                .postEffectiveScore(BigDecimal.valueOf(7))
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();

        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1")).thenReturn(Optional.of(submission));
        when(gradeAuditLogRepository.findBySubmission_IdOrderByCreatedAtDesc("sub-1")).thenReturn(List.of(log2, log1));

        List<GradeAuditLogDto> dtos = teacherScoringService.getAuditLogs("sub-1", "teacher-1");

        assertEquals(2, dtos.size());
        assertEquals("log-2", dtos.get(0).getId());
        assertEquals("OVERRIDE_SET", dtos.get(0).getActionType());
        assertEquals("log-1", dtos.get(1).getId());
        assertEquals("INITIAL_GRADING", dtos.get(1).getActionType());
        assertEquals("teacher1@test.com", dtos.get(0).getActorEmail());
    }

    @Test
    void testNoOpSaveDoesNotRecordAuditLog() {
        submission.setEvaluatedAt(LocalDateTime.now().minusDays(1));
        submission.setStatus("GRADED");
        submission.setTotalScore(BigDecimal.valueOf(8));
        submission.setOverrideScore(BigDecimal.valueOf(7));

        RuleScore existingRule = RuleScore.builder().criteria(criteria).score(BigDecimal.valueOf(8)).build();
        when(ruleScoreRepository.findBySubmission_Id("sub-1")).thenReturn(List.of(existingRule));
        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1")).thenReturn(Optional.of(submission));
        when(scoringCriteriaRepository.findById("crit-1")).thenReturn(Optional.of(criteria));

        SaveScoringPayload payload = new SaveScoringPayload();
        SaveScoringPayload.CriteriaScoreRequest csRequest = new SaveScoringPayload.CriteriaScoreRequest();
        csRequest.setCriterionId("crit-1");
        csRequest.setScore(BigDecimal.valueOf(8));
        payload.setCriteriaScores(List.of(csRequest));
        payload.setOverrideTotalScore(BigDecimal.valueOf(7));

        var result = teacherScoringService.updateScoring("sub-1", "teacher-1", payload);

        assertEquals(BigDecimal.valueOf(8), submission.getTotalScore());
        assertEquals(BigDecimal.valueOf(7), submission.getOverrideScore());
        assertEquals(BigDecimal.valueOf(7), result.getEffectiveScore());
        verify(gradeAuditLogRepository, never()).save(any());
    }

    @Test
    void testConsolidatedRationaleBothFieldsPresent() {
        SaveScoringPayload payload = new SaveScoringPayload();
        SaveScoringPayload.CriteriaScoreRequest csRequest = new SaveScoringPayload.CriteriaScoreRequest();
        csRequest.setCriterionId("crit-1");
        csRequest.setScore(BigDecimal.valueOf(8));
        payload.setCriteriaScores(List.of(csRequest));
        payload.setOverrideRationale("Penalty for late submission");
        payload.setInstructorEvaluationComments("Good attempt on logic");

        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1")).thenReturn(Optional.of(submission));
        when(scoringCriteriaRepository.findById("crit-1")).thenReturn(Optional.of(criteria));
        when(ruleScoreRepository.findBySubmission_Id("sub-1")).thenReturn(new ArrayList<>());

        teacherScoringService.updateScoring("sub-1", "teacher-1", payload);

        ArgumentCaptor<GradeAuditLog> logCaptor = ArgumentCaptor.forClass(GradeAuditLog.class);
        verify(gradeAuditLogRepository).save(logCaptor.capture());
        GradeAuditLog savedLog = logCaptor.getValue();

        assertEquals("Override Rationale: Penalty for late submission\nInstructor Comments: Good attempt on logic", savedLog.getRationale());
    }

    @Test
    void testConsolidatedRationaleOnlyOverrideRationale() {
        SaveScoringPayload payload = new SaveScoringPayload();
        SaveScoringPayload.CriteriaScoreRequest csRequest = new SaveScoringPayload.CriteriaScoreRequest();
        csRequest.setCriterionId("crit-1");
        csRequest.setScore(BigDecimal.valueOf(8));
        payload.setCriteriaScores(List.of(csRequest));
        payload.setOverrideRationale("Only rationale provided");
        payload.setInstructorEvaluationComments(null);

        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1")).thenReturn(Optional.of(submission));
        when(scoringCriteriaRepository.findById("crit-1")).thenReturn(Optional.of(criteria));
        when(ruleScoreRepository.findBySubmission_Id("sub-1")).thenReturn(new ArrayList<>());

        teacherScoringService.updateScoring("sub-1", "teacher-1", payload);

        ArgumentCaptor<GradeAuditLog> logCaptor = ArgumentCaptor.forClass(GradeAuditLog.class);
        verify(gradeAuditLogRepository).save(logCaptor.capture());
        GradeAuditLog savedLog = logCaptor.getValue();

        assertEquals("Only rationale provided", savedLog.getRationale());
    }

    @Test
    void testConsolidatedRationaleOnlyInstructorComments() {
        SaveScoringPayload payload = new SaveScoringPayload();
        SaveScoringPayload.CriteriaScoreRequest csRequest = new SaveScoringPayload.CriteriaScoreRequest();
        csRequest.setCriterionId("crit-1");
        csRequest.setScore(BigDecimal.valueOf(8));
        payload.setCriteriaScores(List.of(csRequest));
        payload.setOverrideRationale("   ");
        payload.setInstructorEvaluationComments("Detailed feedback on implementation");

        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1")).thenReturn(Optional.of(submission));
        when(scoringCriteriaRepository.findById("crit-1")).thenReturn(Optional.of(criteria));
        when(ruleScoreRepository.findBySubmission_Id("sub-1")).thenReturn(new ArrayList<>());

        teacherScoringService.updateScoring("sub-1", "teacher-1", payload);

        ArgumentCaptor<GradeAuditLog> logCaptor = ArgumentCaptor.forClass(GradeAuditLog.class);
        verify(gradeAuditLogRepository).save(logCaptor.capture());
        GradeAuditLog savedLog = logCaptor.getValue();

        assertEquals("Detailed feedback on implementation", savedLog.getRationale());
    }

    @Test
    void testConsolidatedRationaleBothNullOrBlank() {
        SaveScoringPayload payload = new SaveScoringPayload();
        SaveScoringPayload.CriteriaScoreRequest csRequest = new SaveScoringPayload.CriteriaScoreRequest();
        csRequest.setCriterionId("crit-1");
        csRequest.setScore(BigDecimal.valueOf(8));
        payload.setCriteriaScores(List.of(csRequest));
        payload.setOverrideRationale("   ");
        payload.setInstructorEvaluationComments(null);

        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1")).thenReturn(Optional.of(submission));
        when(scoringCriteriaRepository.findById("crit-1")).thenReturn(Optional.of(criteria));
        when(ruleScoreRepository.findBySubmission_Id("sub-1")).thenReturn(new ArrayList<>());

        teacherScoringService.updateScoring("sub-1", "teacher-1", payload);

        ArgumentCaptor<GradeAuditLog> logCaptor = ArgumentCaptor.forClass(GradeAuditLog.class);
        verify(gradeAuditLogRepository).save(logCaptor.capture());
        GradeAuditLog savedLog = logCaptor.getValue();

        assertNull(savedLog.getRationale());
    }

    @Test
    void testGetAuditLogs_TeacherAuthorized_Success() {
        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1"))
                .thenReturn(Optional.of(submission));

        GradeAuditLog auditLog = GradeAuditLog.builder()
                .id("101")
                .submission(submission)
                .actor(teacher)
                .actionType("SCORE_OVERRIDE")
                .preEffectiveScore(BigDecimal.valueOf(7.0))
                .postEffectiveScore(BigDecimal.valueOf(9.0))
                .rationale("Regrading verified")
                .createdAt(LocalDateTime.now())
                .build();

        when(gradeAuditLogRepository.findBySubmission_IdOrderByCreatedAtDesc("sub-1"))
                .thenReturn(List.of(auditLog));

        List<GradeAuditLogDto> result = teacherScoringService.getAuditLogs("sub-1", teacher);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("101", result.get(0).getId());
        assertEquals("SCORE_OVERRIDE", result.get(0).getActionType());
        assertEquals(BigDecimal.valueOf(9.0), result.get(0).getPostEffectiveScore());
    }

    @Test
    void testGetAuditLogs_TeacherUnauthorized_ThrowsResourceNotFoundException() {
        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "other-teacher"))
                .thenReturn(Optional.empty());

        User otherTeacher = User.builder().id("other-teacher").role(com.codeassess.user.entity.Role.TEACHER).build();

        assertThrows(ResourceNotFoundException.class, () ->
                teacherScoringService.getAuditLogs("sub-1", otherTeacher));

        verify(gradeAuditLogRepository, never()).findBySubmission_IdOrderByCreatedAtDesc(anyString());
    }

    @Test
    void testGetAuditLogs_AdminAuthorized_Success() {
        User admin = User.builder().id("admin-1").email("admin@test.com").role(com.codeassess.user.entity.Role.ADMIN).build();

        when(submissionRepository.findById("sub-1")).thenReturn(Optional.of(submission));

        GradeAuditLog auditLog = GradeAuditLog.builder()
                .id("202")
                .submission(submission)
                .actor(admin)
                .actionType("ARBITRATION_UPDATE")
                .preEffectiveScore(BigDecimal.valueOf(6.0))
                .postEffectiveScore(BigDecimal.valueOf(8.5))
                .rationale("Admin appeal arbitration")
                .createdAt(LocalDateTime.now())
                .build();

        when(gradeAuditLogRepository.findBySubmission_IdOrderByCreatedAtDesc("sub-1"))
                .thenReturn(List.of(auditLog));

        List<GradeAuditLogDto> result = teacherScoringService.getAuditLogs("sub-1", admin);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("202", result.get(0).getId());
        assertEquals("ARBITRATION_UPDATE", result.get(0).getActionType());
        verify(submissionRepository, never()).findByIdAndExam_CourseClass_Teacher_Id(anyString(), anyString());
    }

    @Test
    void testGetAuditLogs_AdminSubmissionNotFound_ThrowsResourceNotFoundException() {
        User admin = User.builder().id("admin-1").email("admin@test.com").role(com.codeassess.user.entity.Role.ADMIN).build();

        when(submissionRepository.findById("non-existent")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                teacherScoringService.getAuditLogs("non-existent", admin));
    }
}
