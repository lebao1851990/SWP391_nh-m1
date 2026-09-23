package com.codeassess.scoring.service;

import com.codeassess.classes.entity.CourseClass;
import com.codeassess.exam.entity.Exam;
import com.codeassess.scoring.dto.request.SaveScoringPayload;
import com.codeassess.scoring.entity.ScoringCriteria;
import com.codeassess.scoring.repository.RuleScoreRepository;
import com.codeassess.scoring.repository.ScoringCriteriaRepository;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

class TeacherScoringServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private RuleScoreRepository ruleScoreRepository;

    @Mock
    private ScoringCriteriaRepository scoringCriteriaRepository;

    @InjectMocks
    private TeacherScoringService teacherScoringService;

    private Submission submission;
    private ScoringCriteria criteria;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        User teacher = User.builder().id("teacher-1").build();
        User student = User.builder().id("student-1").build();
        CourseClass courseClass = CourseClass.builder().teacher(teacher).build();
        Exam exam = Exam.builder().courseClass(courseClass).build();
        
        submission = Submission.builder().id("sub-1").student(student).exam(exam).build();
        
        criteria = ScoringCriteria.builder().id("crit-1").maxScore(BigDecimal.TEN).build();
    }

    @Test
    void testNegativeScoreRejected() {
        SaveScoringPayload payload = new SaveScoringPayload();
        SaveScoringPayload.CriteriaScoreRequest csRequest = new SaveScoringPayload.CriteriaScoreRequest();
        csRequest.setCriterionId("crit-1");
        csRequest.setScore(BigDecimal.valueOf(-1));
        payload.setCriteriaScores(List.of(csRequest));

        Mockito.when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1")).thenReturn(Optional.of(submission));
        Mockito.when(scoringCriteriaRepository.findById("crit-1")).thenReturn(Optional.of(criteria));

        assertThrows(IllegalArgumentException.class, () -> teacherScoringService.updateScoring("sub-1", "teacher-1", payload));
    }

    @Test
    void testScoreGreaterThanMaxRejected() {
        SaveScoringPayload payload = new SaveScoringPayload();
        SaveScoringPayload.CriteriaScoreRequest csRequest = new SaveScoringPayload.CriteriaScoreRequest();
        csRequest.setCriterionId("crit-1");
        csRequest.setScore(BigDecimal.valueOf(11));
        payload.setCriteriaScores(List.of(csRequest));

        Mockito.when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1")).thenReturn(Optional.of(submission));
        Mockito.when(scoringCriteriaRepository.findById("crit-1")).thenReturn(Optional.of(criteria));

        assertThrows(IllegalArgumentException.class, () -> teacherScoringService.updateScoring("sub-1", "teacher-1", payload));
    }

    @Test
    void testValidScoreAcceptedAndTotalsCalculated() {
        SaveScoringPayload payload = new SaveScoringPayload();
        SaveScoringPayload.CriteriaScoreRequest csRequest = new SaveScoringPayload.CriteriaScoreRequest();
        csRequest.setCriterionId("crit-1");
        csRequest.setScore(BigDecimal.valueOf(8));
        payload.setCriteriaScores(List.of(csRequest));

        Mockito.when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1")).thenReturn(Optional.of(submission));
        Mockito.when(scoringCriteriaRepository.findById("crit-1")).thenReturn(Optional.of(criteria));
        Mockito.when(ruleScoreRepository.findBySubmission_Id("sub-1")).thenReturn(new ArrayList<>());

        var result = teacherScoringService.updateScoring("sub-1", "teacher-1", payload);

        assertEquals(BigDecimal.valueOf(8), submission.getTotalScore());
        assertEquals(BigDecimal.valueOf(8), result.getEffectiveScore());
    }

    @Test
    void testOverrideScoreChangesEffectiveScore() {
        SaveScoringPayload payload = new SaveScoringPayload();
        SaveScoringPayload.CriteriaScoreRequest csRequest = new SaveScoringPayload.CriteriaScoreRequest();
        csRequest.setCriterionId("crit-1");
        csRequest.setScore(BigDecimal.valueOf(8));
        payload.setCriteriaScores(List.of(csRequest));
        payload.setOverrideTotalScore(BigDecimal.valueOf(5));

        Mockito.when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1")).thenReturn(Optional.of(submission));
        Mockito.when(scoringCriteriaRepository.findById("crit-1")).thenReturn(Optional.of(criteria));
        Mockito.when(ruleScoreRepository.findBySubmission_Id("sub-1")).thenReturn(new ArrayList<>());

        var result = teacherScoringService.updateScoring("sub-1", "teacher-1", payload);

        assertEquals(BigDecimal.valueOf(8), submission.getTotalScore());
        assertEquals(BigDecimal.valueOf(5), submission.getOverrideScore());
        assertEquals(BigDecimal.valueOf(5), result.getEffectiveScore());
    }
}
