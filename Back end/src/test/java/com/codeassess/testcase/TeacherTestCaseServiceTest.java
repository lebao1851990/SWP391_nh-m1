package com.codeassess.testcase;

import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.testcase.dto.request.CreateTestCaseRequest;
import com.codeassess.testcase.dto.request.UpdateTestCaseRequest;
import java.util.List;
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

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 2B Group 6 Targeted Test Extensions
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Teacher who owns exam retrieves sorted test cases mapped to response DTOs")
    void getTestCases_Success_ReturnsSortedList() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1"))
                .thenReturn(Optional.of(exam1));

        TestCase tc1 = TestCase.builder()
                .id("tc-1")
                .exam(exam1)
                .name("TC 1")
                .description("First test")
                .inputData("1 2")
                .expectedOutput("3")
                .points(BigDecimal.valueOf(5))
                .isHidden(false)
                .displayOrder(1)
                .build();

        TestCase tc2 = TestCase.builder()
                .id("tc-2")
                .exam(exam1)
                .name("TC 2")
                .description("Second test")
                .inputData("5 5")
                .expectedOutput("10")
                .points(BigDecimal.valueOf(10))
                .isHidden(true)
                .displayOrder(2)
                .build();

        when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                .thenReturn(List.of(tc1, tc2));

        List<TestCaseResponse> responses = teacherTestCaseService.getTestCases("exam-1", "teacher-1");

        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals("tc-1", responses.get(0).getId());
        assertEquals("exam-1", responses.get(0).getExamId());
        assertEquals("TC 1", responses.get(0).getName());
        assertFalse(responses.get(0).getIsHidden());
        assertEquals("tc-2", responses.get(1).getId());
        assertTrue(responses.get(1).getIsHidden());
        verify(testCaseRepository).findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1");
    }

    @Test
    @DisplayName("Non-owner teacher cannot retrieve test cases (ResourceNotFoundException)")
    void getTestCases_NonOwner_ThrowsResourceNotFound() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-2"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                teacherTestCaseService.getTestCases("exam-1", "teacher-2")
        );
        verify(testCaseRepository, never()).findByExam_IdOrderByDisplayOrderAscCreatedAtAsc(any());
    }

    @Test
    @DisplayName("createTestCase defaults points to 10.00 and displayOrder to 0 when null in request")
    void createTestCase_DefaultPointsAndDisplayOrder_WhenNull() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1"))
                .thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

        CreateTestCaseRequest request = CreateTestCaseRequest.builder()
                .name("Default Values TC")
                .description("Desc")
                .inputData("in")
                .expectedOutput("out")
                .points(null)
                .displayOrder(null)
                .isHidden(null)
                .build();

        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(i -> {
            TestCase tc = i.getArgument(0);
            tc.setId("tc-default");
            return tc;
        });

        TestCaseResponse response = teacherTestCaseService.createTestCase("exam-1", "teacher-1", request);

        assertNotNull(response);
        assertEquals("tc-default", response.getId());
        assertEquals(BigDecimal.valueOf(10.00), response.getPoints());
        assertEquals(0, response.getDisplayOrder());
        assertFalse(response.getIsHidden());
        verify(testCaseRepository).save(any(TestCase.class));
    }

    @Test
    @DisplayName("createTestCase throws IllegalArgumentException when points is negative")
    void createTestCase_NegativePoints_ThrowsIllegalArgumentException() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1"))
                .thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

        CreateTestCaseRequest request = CreateTestCaseRequest.builder()
                .name("Negative TC")
                .points(BigDecimal.valueOf(-1.0))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                teacherTestCaseService.createTestCase("exam-1", "teacher-1", request)
        );
        assertTrue(ex.getMessage().contains("không được âm"));
        verify(testCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTestCase sets isHidden correctly when explicitly true")
    void createTestCase_WithIsHiddenTrue() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1"))
                .thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

        CreateTestCaseRequest request = CreateTestCaseRequest.builder()
                .name("Secret TC")
                .points(BigDecimal.valueOf(15))
                .isHidden(true)
                .displayOrder(3)
                .build();

        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(i -> {
            TestCase tc = i.getArgument(0);
            tc.setId("tc-secret");
            return tc;
        });

        TestCaseResponse response = teacherTestCaseService.createTestCase("exam-1", "teacher-1", request);

        assertNotNull(response);
        assertTrue(response.getIsHidden());
        assertEquals(3, response.getDisplayOrder());
    }

    @Test
    @DisplayName("updateTestCase successfully updates all fields when no submissions exist")
    void updateTestCase_Success() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1"))
                .thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

        TestCase existing = TestCase.builder()
                .id("tc-1")
                .exam(exam1)
                .name("Old Name")
                .description("Old Desc")
                .inputData("old in")
                .expectedOutput("old out")
                .points(BigDecimal.valueOf(10))
                .isHidden(false)
                .displayOrder(1)
                .build();

        when(testCaseRepository.findByIdAndExam_Id("tc-1", "exam-1")).thenReturn(Optional.of(existing));
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(i -> i.getArgument(0));

        UpdateTestCaseRequest request = UpdateTestCaseRequest.builder()
                .name("Updated Name")
                .description("Updated Desc")
                .inputData("new in")
                .expectedOutput("new out")
                .points(BigDecimal.valueOf(25))
                .isHidden(true)
                .displayOrder(5)
                .build();

        TestCaseResponse response = teacherTestCaseService.updateTestCase("exam-1", "tc-1", "teacher-1", request);

        assertNotNull(response);
        assertEquals("Updated Name", response.getName());
        assertEquals("Updated Desc", response.getDescription());
        assertEquals("new in", response.getInputData());
        assertEquals("new out", response.getExpectedOutput());
        assertEquals(BigDecimal.valueOf(25), response.getPoints());
        assertTrue(response.getIsHidden());
        assertEquals(5, response.getDisplayOrder());
        verify(testCaseRepository).save(existing);
    }

    @Test
    @DisplayName("updateTestCase blocked when teacher is non-owner (ResourceNotFoundException)")
    void updateTestCase_NonOwner_ThrowsResourceNotFound() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-2"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                teacherTestCaseService.updateTestCase("exam-1", "tc-1", "teacher-2", new UpdateTestCaseRequest())
        );
        verify(testCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateTestCase blocked when exam already has submissions (IllegalStateException)")
    void updateTestCase_BlockedWhenSubmissionsExist() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1"))
                .thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(2L);

        UpdateTestCaseRequest request = UpdateTestCaseRequest.builder().name("Name").build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                teacherTestCaseService.updateTestCase("exam-1", "tc-1", "teacher-1", request)
        );
        assertTrue(ex.getMessage().contains("đã có"));
        verify(testCaseRepository, never()).findByIdAndExam_Id(any(), any());
        verify(testCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateTestCase throws ResourceNotFoundException when test case does not exist in exam")
    void updateTestCase_NotFound_ThrowsResourceNotFound() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1"))
                .thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);
        when(testCaseRepository.findByIdAndExam_Id("tc-999", "exam-1")).thenReturn(Optional.empty());

        UpdateTestCaseRequest request = UpdateTestCaseRequest.builder().name("Name").build();

        assertThrows(ResourceNotFoundException.class, () ->
                teacherTestCaseService.updateTestCase("exam-1", "tc-999", "teacher-1", request)
        );
        verify(testCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateTestCase throws IllegalArgumentException when points is negative")
    void updateTestCase_NegativePoints_ThrowsIllegalArgumentException() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1"))
                .thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

        TestCase existing = TestCase.builder()
                .id("tc-1")
                .exam(exam1)
                .name("Old")
                .points(BigDecimal.valueOf(10))
                .build();
        when(testCaseRepository.findByIdAndExam_Id("tc-1", "exam-1")).thenReturn(Optional.of(existing));

        UpdateTestCaseRequest request = UpdateTestCaseRequest.builder()
                .name("Updated")
                .points(BigDecimal.valueOf(-5))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                teacherTestCaseService.updateTestCase("exam-1", "tc-1", "teacher-1", request)
        );
        assertTrue(ex.getMessage().contains("không được âm"));
        verify(testCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateTestCase preserves existing points and displayOrder when null in request")
    void updateTestCase_PreservesExistingPointsAndDisplayOrder_WhenNullInRequest() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1"))
                .thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

        TestCase existing = TestCase.builder()
                .id("tc-1")
                .exam(exam1)
                .name("Old")
                .points(BigDecimal.valueOf(18.50))
                .displayOrder(4)
                .isHidden(true)
                .build();
        when(testCaseRepository.findByIdAndExam_Id("tc-1", "exam-1")).thenReturn(Optional.of(existing));
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(i -> i.getArgument(0));

        UpdateTestCaseRequest request = UpdateTestCaseRequest.builder()
                .name("New Name")
                .points(null)
                .displayOrder(null)
                .isHidden(false)
                .build();

        TestCaseResponse response = teacherTestCaseService.updateTestCase("exam-1", "tc-1", "teacher-1", request);

        assertNotNull(response);
        assertEquals("New Name", response.getName());
        assertEquals(BigDecimal.valueOf(18.50), response.getPoints());
        assertEquals(4, response.getDisplayOrder());
        assertFalse(response.getIsHidden());
        verify(testCaseRepository).save(existing);
    }

    @Test
    @DisplayName("deleteTestCase successfully deletes testcase when no submissions exist")
    void deleteTestCase_Success() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1"))
                .thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

        TestCase existing = TestCase.builder()
                .id("tc-1")
                .exam(exam1)
                .name("To Delete")
                .build();
        when(testCaseRepository.findByIdAndExam_Id("tc-1", "exam-1")).thenReturn(Optional.of(existing));

        assertDoesNotThrow(() -> teacherTestCaseService.deleteTestCase("exam-1", "tc-1", "teacher-1"));
        verify(testCaseRepository).delete(existing);
    }

    @Test
    @DisplayName("deleteTestCase blocked when teacher is non-owner (ResourceNotFoundException)")
    void deleteTestCase_NonOwner_ThrowsResourceNotFound() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-2"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                teacherTestCaseService.deleteTestCase("exam-1", "tc-1", "teacher-2")
        );
        verify(testCaseRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteTestCase blocked when exam already has submissions (IllegalStateException)")
    void deleteTestCase_BlockedWhenSubmissionsExist() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1"))
                .thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(5L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                teacherTestCaseService.deleteTestCase("exam-1", "tc-1", "teacher-1")
        );
        assertTrue(ex.getMessage().contains("đã có"));
        verify(testCaseRepository, never()).findByIdAndExam_Id(any(), any());
        verify(testCaseRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteTestCase throws ResourceNotFoundException when testcase does not exist in exam")
    void deleteTestCase_NotFound_ThrowsResourceNotFound() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1"))
                .thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);
        when(testCaseRepository.findByIdAndExam_Id("tc-999", "exam-1")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                teacherTestCaseService.deleteTestCase("exam-1", "tc-999", "teacher-1")
        );
        verify(testCaseRepository, never()).delete(any());
    }
}
