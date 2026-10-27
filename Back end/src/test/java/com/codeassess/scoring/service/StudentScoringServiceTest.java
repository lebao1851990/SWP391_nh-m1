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
    private com.codeassess.testcase.repository.SubmissionTestResultRepository submissionTestResultRepository;

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
        submission.setTotalScore(BigDecimal.valueOf(7.5));
        submission.setOverrideScore(BigDecimal.valueOf(8.5));

        Mockito.when(submissionRepository.findByIdAndStudent_Id("sub-1", "student-1")).thenReturn(Optional.of(submission));
        Mockito.when(ruleScoreRepository.findBySubmission_Id("sub-1")).thenReturn(new ArrayList<>());

        var result = studentScoringService.getStudentResult("sub-1", "student-1");

        assertEquals(BigDecimal.valueOf(7.5), result.getTotalScore());
        assertEquals(BigDecimal.valueOf(8.5), result.getOverrideTotalScore());
        assertEquals(BigDecimal.valueOf(8.5), result.getEffectiveScore());
    }

    @Test
    void testEffectiveScoreCalculationWithoutOverride() {
        submission.setTotalScore(BigDecimal.valueOf(7.5));
        submission.setOverrideScore(null);

        Mockito.when(submissionRepository.findByIdAndStudent_Id("sub-1", "student-1")).thenReturn(Optional.of(submission));
        Mockito.when(ruleScoreRepository.findBySubmission_Id("sub-1")).thenReturn(new ArrayList<>());

        var result = studentScoringService.getStudentResult("sub-1", "student-1");

        assertEquals(BigDecimal.valueOf(7.5), result.getTotalScore());
        assertEquals(null, result.getOverrideTotalScore());
        assertEquals(BigDecimal.valueOf(7.5), result.getEffectiveScore());
    }
}
