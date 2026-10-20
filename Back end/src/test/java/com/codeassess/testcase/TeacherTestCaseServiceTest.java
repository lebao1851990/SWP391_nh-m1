package com.codeassess.testcase;

import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.testcase.dto.request.CreateTestCaseRequest;
import com.codeassess.testcase.dto.response.TestCaseResponse;
import com.codeassess.testcase.entity.TestCase;
import com.codeassess.testcase.repository.TestCaseRepository;
import com.codeassess.testcase.service.TeacherTestCaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.codeassess.common.exception.ResourceNotFoundException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeacherTestCaseService Unit Tests")
class TeacherTestCaseServiceTest {

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private ExamRepository examRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    private TeacherTestCaseService teacherTestCaseService;

    private Exam exam1;

    @BeforeEach
    void setUp() {
        teacherTestCaseService = new TeacherTestCaseService(
                testCaseRepository,
                examRepository,
                submissionRepository
        );

        exam1 = new Exam();
        exam1.setId("exam-1");
        exam1.setTitle("Midterm Java");
    }

    @Test
    @DisplayName("Teacher who owns the exam can create test case when no submissions exist")
    void testCreateTestCaseSuccess() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1"))
                .thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

        CreateTestCaseRequest request = new CreateTestCaseRequest();
        request.setName("Sample TC");
        request.setInputData("1 2");
        request.setExpectedOutput("3");
        request.setPoints(BigDecimal.valueOf(10));
        request.setIsHidden(false);
        request.setDisplayOrder(1);

        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(invocation -> {
            TestCase saved = invocation.getArgument(0);
            saved.setId("tc-100");
            return saved;
        });

        TestCaseResponse response = teacherTestCaseService.createTestCase("exam-1", "teacher-1", request);

        assertNotNull(response);
        assertEquals("tc-100", response.getId());
        assertEquals("Sample TC", response.getName());
        assertEquals("3", response.getExpectedOutput());
        verify(testCaseRepository).save(any(TestCase.class));
    }

    @Test
    @DisplayName("Teacher who does not own the exam cannot create test case (ResourceNotFoundException)")
    void testCreateTestCaseByNonOwnerDenied() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-2"))
                .thenReturn(Optional.empty());

        CreateTestCaseRequest request = new CreateTestCaseRequest();
        request.setName("Sample TC");

        assertThrows(ResourceNotFoundException.class, () ->
                teacherTestCaseService.createTestCase("exam-1", "teacher-2", request)
        );
        verify(testCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cannot create test cases when exam already has submissions")
    void testCreateTestCaseBlockedWhenSubmissionsExist() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1"))
                .thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(3L);

        CreateTestCaseRequest request = new CreateTestCaseRequest();
        request.setName("Late TC");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                teacherTestCaseService.createTestCase("exam-1", "teacher-1", request)
        );
        assertTrue(ex.getMessage().contains("đã có"));
        verify(testCaseRepository, never()).save(any());
    }
}
