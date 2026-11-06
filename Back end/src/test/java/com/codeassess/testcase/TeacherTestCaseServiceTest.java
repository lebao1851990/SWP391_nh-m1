package com.codeassess.testcase;

import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.entity.ExamQuestion;
import com.codeassess.exam.repository.ExamQuestionRepository;
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

    @Mock
    private ExamQuestionRepository examQuestionRepository;

    private TeacherTestCaseService teacherTestCaseService;

    private Exam exam1;

    @BeforeEach
    void setUp() {
        teacherTestCaseService = new TeacherTestCaseService(
                testCaseRepository,
                examRepository,
                submissionRepository,
                examQuestionRepository
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

    // ─────────────────────────────────────────────────────────────────────────
    // TASK-FE-AI-EXAM-04 Question-Scoped Test Case Score Validation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createTestCase Question-Scoped: TC points exceeds question maxScore -> throws IllegalArgumentException")
    void createTestCase_QuestionScoped_SingleTCExceedsQuestionMaxScore() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1"))
                .thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

        ExamQuestion question = ExamQuestion.builder()
                .id("q-1")
                .exam(exam1)
                .title("Two Sum")
                .maxScore(BigDecimal.valueOf(3.00))
                .build();
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-1", "exam-1"))
                .thenReturn(Optional.of(question));

        CreateTestCaseRequest request = CreateTestCaseRequest.builder()
                .questionId("q-1")
                .name("Exceeding TC")
                .points(BigDecimal.valueOf(3.50))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                teacherTestCaseService.createTestCase("exam-1", "teacher-1", request)
        );
        assertTrue(ex.getMessage().contains("không được vượt quá điểm tối đa của câu hỏi"));
        verify(testCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTestCase Question-Scoped: Sum of TC points exceeds question maxScore -> throws IllegalArgumentException")
    void createTestCase_QuestionScoped_SumExceedsQuestionMaxScore() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1"))
                .thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

        ExamQuestion question = ExamQuestion.builder()
                .id("q-1")
                .exam(exam1)
                .title("Two Sum")
                .maxScore(BigDecimal.valueOf(3.00))
                .build();
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-1", "exam-1"))
                .thenReturn(Optional.of(question));

        TestCase existingTC = TestCase.builder()
                .id("tc-1")
                .points(BigDecimal.valueOf(2.00))
                .question(question)
                .build();
        when(testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc("q-1"))
                .thenReturn(List.of(existingTC));

        CreateTestCaseRequest request = CreateTestCaseRequest.builder()
                .questionId("q-1")
                .name("TC 2")
                .points(BigDecimal.valueOf(1.50))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                teacherTestCaseService.createTestCase("exam-1", "teacher-1", request)
        );
        assertTrue(ex.getMessage().contains("Tổng điểm các test case"));
        verify(testCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTestCase Question-Scoped: Sum equals question maxScore -> success")
    void createTestCase_QuestionScoped_Success() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1"))
                .thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

        ExamQuestion question = ExamQuestion.builder()
                .id("q-1")
                .exam(exam1)
                .title("Two Sum")
                .maxScore(BigDecimal.valueOf(3.00))
                .build();
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-1", "exam-1"))
                .thenReturn(Optional.of(question));

        TestCase existingTC = TestCase.builder()
                .id("tc-1")
                .points(BigDecimal.valueOf(1.50))
                .question(question)
                .build();
        when(testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc("q-1"))
                .thenReturn(List.of(existingTC));

        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(i -> {
            TestCase tc = i.getArgument(0);
            tc.setId("tc-2");
            return tc;
        });

        CreateTestCaseRequest request = CreateTestCaseRequest.builder()
                .questionId("q-1")
                .name("TC 2")
                .points(BigDecimal.valueOf(1.50))
                .build();

        TestCaseResponse response = teacherTestCaseService.createTestCase("exam-1", "teacher-1", request);
        assertNotNull(response);
        assertEquals("tc-2", response.getId());
        assertEquals("q-1", response.getQuestionId());
        assertEquals(BigDecimal.valueOf(1.50), response.getPoints());
        verify(testCaseRepository).save(any(TestCase.class));
    }

    @Test
    @DisplayName("updateTestCase Question-Scoped: Updating TC points causing sum to exceed maxScore -> throws IllegalArgumentException")
    void updateTestCase_QuestionScoped_ExceedsSum() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1"))
                .thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

        ExamQuestion question = ExamQuestion.builder()
                .id("q-1")
                .exam(exam1)
                .maxScore(BigDecimal.valueOf(3.00))
                .build();

        TestCase tc1 = TestCase.builder().id("tc-1").points(BigDecimal.valueOf(1.00)).question(question).build();
        TestCase tc2 = TestCase.builder().id("tc-2").exam(exam1).points(BigDecimal.valueOf(1.00)).question(question).build();

        when(testCaseRepository.findByIdAndExam_Id("tc-2", "exam-1")).thenReturn(Optional.of(tc2));
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-1", "exam-1")).thenReturn(Optional.of(question));
        when(testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc("q-1")).thenReturn(List.of(tc1, tc2));

        UpdateTestCaseRequest request = UpdateTestCaseRequest.builder()
                .points(BigDecimal.valueOf(2.50))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                teacherTestCaseService.updateTestCase("exam-1", "tc-2", "teacher-1", request)
        );
        assertTrue(ex.getMessage().contains("Tổng điểm các test case"));
        verify(testCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateTestCase Question-Scoped: Reallocating TC points within maxScore -> success")
    void updateTestCase_QuestionScoped_Success() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1"))
                .thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

        ExamQuestion question = ExamQuestion.builder()
                .id("q-1")
                .exam(exam1)
                .maxScore(BigDecimal.valueOf(3.00))
                .build();

        TestCase tc1 = TestCase.builder().id("tc-1").points(BigDecimal.valueOf(1.00)).question(question).build();
        TestCase tc2 = TestCase.builder().id("tc-2").points(BigDecimal.valueOf(1.00)).question(question).build();

        when(testCaseRepository.findByIdAndExam_Id("tc-2", "exam-1")).thenReturn(Optional.of(tc2));
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-1", "exam-1")).thenReturn(Optional.of(question));
        when(testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc("q-1")).thenReturn(List.of(tc1, tc2));
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(i -> i.getArgument(0));

        UpdateTestCaseRequest request = UpdateTestCaseRequest.builder()
                .points(BigDecimal.valueOf(2.00))
                .build();

        TestCaseResponse response = teacherTestCaseService.updateTestCase("exam-1", "tc-2", "teacher-1", request);
        assertNotNull(response);
        assertEquals(BigDecimal.valueOf(2.00), response.getPoints());
        assertEquals("q-1", response.getQuestionId());
        verify(testCaseRepository).save(tc2);
    }

    @Test
    @DisplayName("Phase B: updateTestCase Question Transfer: Locks source and target in deterministic ascending ID order and saves transferred TC")
    void updateTestCase_QuestionTransfer_Success_DeterministicAscendingLock() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1"))
                .thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

        ExamQuestion q1 = ExamQuestion.builder().id("q-1").exam(exam1).title("Q1").maxScore(BigDecimal.valueOf(5.00)).build();
        ExamQuestion q2 = ExamQuestion.builder().id("q-2").exam(exam1).title("Q2").maxScore(BigDecimal.valueOf(5.00)).build();

        TestCase tcToTransfer = TestCase.builder()
                .id("tc-x")
                .exam(exam1)
                .question(q1)
                .points(BigDecimal.valueOf(1.00))
                .name("Transfer Me")
                .build();

        TestCase existingInQ2 = TestCase.builder()
                .id("tc-q2-1")
                .exam(exam1)
                .question(q2)
                .points(BigDecimal.valueOf(1.50))
                .build();

        when(testCaseRepository.findByIdAndExam_Id("tc-x", "exam-1")).thenReturn(Optional.of(tcToTransfer));
        // Both questions locked in ascending ID order: "q-1" then "q-2"
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-1", "exam-1")).thenReturn(Optional.of(q1));
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-2", "exam-1")).thenReturn(Optional.of(q2));
        when(testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc("q-2")).thenReturn(List.of(existingInQ2));
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(i -> i.getArgument(0));

        UpdateTestCaseRequest request = UpdateTestCaseRequest.builder()
                .questionId("q-2")
                .points(BigDecimal.valueOf(2.00))
                .build();

        TestCaseResponse response = teacherTestCaseService.updateTestCase("exam-1", "tc-x", "teacher-1", request);

        assertNotNull(response);
        assertEquals("q-2", response.getQuestionId());
        assertEquals(BigDecimal.valueOf(2.00), response.getPoints());
        assertEquals(q2, tcToTransfer.getQuestion());

        // Verify both questions were locked in ascending order
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(examQuestionRepository);
        inOrder.verify(examQuestionRepository).findByIdAndExam_IdForUpdate("q-1", "exam-1");
        inOrder.verify(examQuestionRepository).findByIdAndExam_IdForUpdate("q-2", "exam-1");

        verify(testCaseRepository).save(tcToTransfer);
    }

    @Test
    @DisplayName("Phase B: updateTestCase Question Transfer (reverse direction Q2 -> Q1): Still locks in ascending ID order (Q1 then Q2) to prevent deadlock")
    void updateTestCase_QuestionTransfer_ReverseDirection_AscendingLockPrevention() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1"))
                .thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

        ExamQuestion q1 = ExamQuestion.builder().id("q-1").exam(exam1).title("Q1").maxScore(BigDecimal.valueOf(5.00)).build();
        ExamQuestion q2 = ExamQuestion.builder().id("q-2").exam(exam1).title("Q2").maxScore(BigDecimal.valueOf(5.00)).build();

        TestCase tcInQ2 = TestCase.builder()
                .id("tc-from-q2")
                .exam(exam1)
                .question(q2)
                .points(BigDecimal.valueOf(1.00))
                .build();

        when(testCaseRepository.findByIdAndExam_Id("tc-from-q2", "exam-1")).thenReturn(Optional.of(tcInQ2));
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-1", "exam-1")).thenReturn(Optional.of(q1));
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-2", "exam-1")).thenReturn(Optional.of(q2));
        when(testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc("q-1")).thenReturn(List.of());
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(i -> i.getArgument(0));

        UpdateTestCaseRequest request = UpdateTestCaseRequest.builder()
                .questionId("q-1")
                .points(BigDecimal.valueOf(3.00))
                .build();

        TestCaseResponse response = teacherTestCaseService.updateTestCase("exam-1", "tc-from-q2", "teacher-1", request);

        assertNotNull(response);
        assertEquals("q-1", response.getQuestionId());
        assertEquals(BigDecimal.valueOf(3.00), response.getPoints());
        assertEquals(q1, tcInQ2.getQuestion());

        // In reverse transfer (Q2 -> Q1), first lock requested MUST STILL BE Q1 (lexicographical / ascending order)
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(examQuestionRepository);
        inOrder.verify(examQuestionRepository).findByIdAndExam_IdForUpdate("q-1", "exam-1");
        inOrder.verify(examQuestionRepository).findByIdAndExam_IdForUpdate("q-2", "exam-1");

        verify(testCaseRepository).save(tcInQ2);
    }

    @Test
    @DisplayName("Phase B: updateTestCase Question Transfer: Target question score exceeded throws IllegalArgumentException and never saves")
    void updateTestCase_QuestionTransfer_TargetExceedsMaxScore_Rollback() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1"))
                .thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

        ExamQuestion q1 = ExamQuestion.builder().id("q-1").exam(exam1).maxScore(BigDecimal.valueOf(5.00)).build();
        ExamQuestion q2 = ExamQuestion.builder().id("q-2").exam(exam1).maxScore(BigDecimal.valueOf(3.00)).build();

        TestCase tcInQ1 = TestCase.builder().id("tc-1").exam(exam1).question(q1).points(BigDecimal.valueOf(1.00)).build();
        TestCase existingInQ2 = TestCase.builder().id("tc-2").exam(exam1).question(q2).points(BigDecimal.valueOf(2.50)).build();

        when(testCaseRepository.findByIdAndExam_Id("tc-1", "exam-1")).thenReturn(Optional.of(tcInQ1));
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-1", "exam-1")).thenReturn(Optional.of(q1));
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-2", "exam-1")).thenReturn(Optional.of(q2));
        when(testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc("q-2")).thenReturn(List.of(existingInQ2));

        // Requesting 1.00 points into q2 (which already has 2.50 / max 3.00) -> 3.50 > 3.00
        UpdateTestCaseRequest request = UpdateTestCaseRequest.builder()
                .questionId("q-2")
                .points(BigDecimal.valueOf(1.00))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                teacherTestCaseService.updateTestCase("exam-1", "tc-1", "teacher-1", request)
        );

        assertTrue(ex.getMessage().contains("Tổng điểm các test case (3.50) vượt quá điểm tối đa"));
        verify(testCaseRepository, never()).save(any());
        // Verify original question unchanged
        assertEquals(q1, tcInQ1.getQuestion());
    }

    @Test
    @DisplayName("Phase B: updateTestCase Question Transfer: Target question not in exam throws ResourceNotFoundException")
    void updateTestCase_QuestionTransfer_TargetNotFound_ThrowsNotFound() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1"))
                .thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

        ExamQuestion q1 = ExamQuestion.builder().id("q-1").exam(exam1).build();
        TestCase tcInQ1 = TestCase.builder().id("tc-1").exam(exam1).question(q1).points(BigDecimal.valueOf(1.00)).build();

        when(testCaseRepository.findByIdAndExam_Id("tc-1", "exam-1")).thenReturn(Optional.of(tcInQ1));
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-1", "exam-1")).thenReturn(Optional.of(q1));
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-other-exam", "exam-1")).thenReturn(Optional.empty());

        UpdateTestCaseRequest request = UpdateTestCaseRequest.builder()
                .questionId("q-other-exam")
                .build();

        assertThrows(ResourceNotFoundException.class, () ->
                teacherTestCaseService.updateTestCase("exam-1", "tc-1", "teacher-1", request)
        );

        verify(testCaseRepository, never()).save(any());
        assertEquals(q1, tcInQ1.getQuestion());
    }

    @Test
    @DisplayName("FE-VAL-01B: createTestCase with whitespace-only input throws IllegalArgumentException")
    void createTestCase_WhitespaceOnlyInput_ThrowsException() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

        CreateTestCaseRequest request = CreateTestCaseRequest.builder()
                .name("TC 1")
                .inputData("   \t\n  ")
                .expectedOutput("Hello")
                .points(BigDecimal.valueOf(1.00))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                teacherTestCaseService.createTestCase("exam-1", "teacher-1", request)
        );
        assertTrue(ex.getMessage().contains("Dữ liệu đầu vào (Input Data) không được chỉ chứa ký tự khoảng trắng"));
        verify(testCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("FE-VAL-01B: createTestCase with whitespace-only expectedOutput throws IllegalArgumentException")
    void createTestCase_WhitespaceOnlyExpectedOutput_ThrowsException() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

        CreateTestCaseRequest request = CreateTestCaseRequest.builder()
                .name("TC 1")
                .inputData("5")
                .expectedOutput("   \n  ")
                .points(BigDecimal.valueOf(1.00))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                teacherTestCaseService.createTestCase("exam-1", "teacher-1", request)
        );
        assertTrue(ex.getMessage().contains("Kết quả mong đợi (Expected Output) không được chỉ chứa ký tự khoảng trắng"));
        verify(testCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("FE-VAL-01B: updateTestCase with whitespace-only input throws IllegalArgumentException")
    void updateTestCase_WhitespaceOnlyInput_ThrowsException() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

        TestCase tc = TestCase.builder().id("tc-1").exam(exam1).points(BigDecimal.valueOf(1.00)).build();
        when(testCaseRepository.findByIdAndExam_Id("tc-1", "exam-1")).thenReturn(Optional.of(tc));

        UpdateTestCaseRequest request = UpdateTestCaseRequest.builder()
                .inputData("   ")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                teacherTestCaseService.updateTestCase("exam-1", "tc-1", "teacher-1", request)
        );
        assertTrue(ex.getMessage().contains("Dữ liệu đầu vào (Input Data) không được chỉ chứa ký tự khoảng trắng"));
        verify(testCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("FE-VAL-01B: updateTestCase with whitespace-only expectedOutput throws IllegalArgumentException")
    void updateTestCase_WhitespaceOnlyExpectedOutput_ThrowsException() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

        TestCase tc = TestCase.builder().id("tc-1").exam(exam1).points(BigDecimal.valueOf(1.00)).build();
        when(testCaseRepository.findByIdAndExam_Id("tc-1", "exam-1")).thenReturn(Optional.of(tc));

        UpdateTestCaseRequest request = UpdateTestCaseRequest.builder()
                .expectedOutput(" \t ")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                teacherTestCaseService.updateTestCase("exam-1", "tc-1", "teacher-1", request)
        );
        assertTrue(ex.getMessage().contains("Kết quả mong đợi (Expected Output) không được chỉ chứa ký tự khoảng trắng"));
        verify(testCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("FE-VAL-01B: createTestCase with points exceeding 2 decimal places throws IllegalArgumentException")
    void createTestCase_PointsExceedingTwoDecimals_ThrowsException() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

        CreateTestCaseRequest request = CreateTestCaseRequest.builder()
                .name("TC Decimal")
                .expectedOutput("42")
                .points(new BigDecimal("2.555"))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                teacherTestCaseService.createTestCase("exam-1", "teacher-1", request)
        );
        assertTrue(ex.getMessage().contains("không được vượt quá 2 chữ số thập phân"));
        verify(testCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("FE-VAL-01B: createTestCase with points exceeding maximum 999.99 throws IllegalArgumentException")
    void createTestCase_PointsExceedingMaxBound_ThrowsException() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

        CreateTestCaseRequest request = CreateTestCaseRequest.builder()
                .name("TC Huge")
                .expectedOutput("42")
                .points(new BigDecimal("1000.00"))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                teacherTestCaseService.createTestCase("exam-1", "teacher-1", request)
        );
        assertTrue(ex.getMessage().contains("vượt quá giới hạn tối đa cho phép"));
        verify(testCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("FE-VAL-01B: updateTestCase with points exceeding 2 decimal places throws IllegalArgumentException")
    void updateTestCase_PointsExceedingTwoDecimals_ThrowsException() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam1));
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

        TestCase tc = TestCase.builder().id("tc-1").exam(exam1).points(BigDecimal.valueOf(1.00)).build();
        when(testCaseRepository.findByIdAndExam_Id("tc-1", "exam-1")).thenReturn(Optional.of(tc));

        UpdateTestCaseRequest request = UpdateTestCaseRequest.builder()
                .points(new BigDecimal("2.501"))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                teacherTestCaseService.updateTestCase("exam-1", "tc-1", "teacher-1", request)
        );
        assertTrue(ex.getMessage().contains("không được vượt quá 2 chữ số thập phân"));
        verify(testCaseRepository, never()).save(any());
    }
}
