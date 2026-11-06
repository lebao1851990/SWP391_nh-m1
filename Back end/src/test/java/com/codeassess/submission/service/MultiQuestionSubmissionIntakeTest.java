package com.codeassess.submission.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.entity.ExamQuestion;
import com.codeassess.exam.repository.ExamQuestionRepository;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.scoring.repository.StudentQuestionScoreRepository;
import com.codeassess.submission.dto.request.SubmissionCreateRequest;
import com.codeassess.submission.dto.response.SubmissionDetailResponse;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.mapper.SubmissionMapper;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiQuestionSubmissionIntakeTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private SubmissionMapper submissionMapper;

    @Mock
    private ExamRepository examRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ExamQuestionRepository examQuestionRepository;

    @Mock
    private StudentQuestionScoreRepository studentQuestionScoreRepository;

    @Mock
    private SubmissionPersistenceService submissionPersistenceService;

    private StudentSubmissionService studentSubmissionService;

    private Exam testExam;
    private ExamQuestion testQuestion1;
    private ExamQuestion testQuestion2;
    private User testStudent;

    @BeforeEach
    void setUp() {
        studentSubmissionService = new StudentSubmissionService(
                submissionRepository,
                submissionMapper,
                examRepository,
                userRepository,
                eventPublisher
        );
        ReflectionTestUtils.setField(studentSubmissionService, "examQuestionRepository", examQuestionRepository);
        ReflectionTestUtils.setField(studentSubmissionService, "studentQuestionScoreRepository", studentQuestionScoreRepository);
        ReflectionTestUtils.setField(studentSubmissionService, "submissionPersistenceService", submissionPersistenceService);

        testExam = Exam.builder()
                .id("exam-1")
                .title("Midterm Exam")
                .status("active")
                .examDate(LocalDateTime.now().minusHours(1))
                .duration(120)
                .build();

        testQuestion1 = ExamQuestion.builder()
                .id("q-1")
                .exam(testExam)
                .title("Question 1")
                .status("PUBLISHED")
                .maxScore(BigDecimal.valueOf(5.00))
                .displayOrder(1)
                .build();

        testQuestion2 = ExamQuestion.builder()
                .id("q-2")
                .exam(testExam)
                .title("Question 2")
                .status("DRAFT")
                .maxScore(BigDecimal.valueOf(5.00))
                .displayOrder(2)
                .build();

        testStudent = User.builder()
                .id("student-1")
                .email("alice@codeassess.com")
                .fullName("Alice Student")
                .build();
    }

    @Test
    @DisplayName("Submit Code with explicit Question 1 succeeds and triggers atomic sequence upsert")
    void testSubmitCode_WithExplicitQuestion_Success() {
        when(examRepository.findByIdForShare("exam-1")).thenReturn(Optional.of(testExam));
        when(examRepository.findByIdAndStudentId("exam-1", "student-1")).thenReturn(Optional.of(testExam));
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-1", "exam-1")).thenReturn(Optional.of(testQuestion1));
        when(userRepository.findById("student-1")).thenReturn(Optional.of(testStudent));

        Submission savedSub = Submission.builder()
                .id("sub-100")
                .exam(testExam)
                .question(testQuestion1)
                .student(testStudent)
                .status("PENDING")
                .build();

        when(submissionRepository.save(any(Submission.class))).thenReturn(savedSub);
        when(submissionMapper.toDetailResponse(savedSub)).thenReturn(
                SubmissionDetailResponse.builder().id("sub-100").status("PENDING").build()
        );

        SubmissionCreateRequest request = SubmissionCreateRequest.builder()
                .sourceCode("public class Main { public static void main(String[] args) {} }")
                .questionId("q-1")
                .build();

        SubmissionDetailResponse response = studentSubmissionService.submitCode("exam-1", "student-1", request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("sub-100");

        // Verify atomic sequence upsert was executed with correct parameters
        verify(studentQuestionScoreRepository).upsertIntakeSequence(
                anyString(),
                eq("exam-1"),
                eq("q-1"),
                eq("student-1"),
                eq("sub-100")
        );
    }

    @Test
    @DisplayName("Submit Code without questionId resolves to first exam question for backward compatibility")
    void testSubmitCode_WithoutQuestionId_ResolvesToFirstQuestion() {
        when(examRepository.findByIdForShare("exam-1")).thenReturn(Optional.of(testExam));
        when(examRepository.findByIdAndStudentId("exam-1", "student-1")).thenReturn(Optional.of(testExam));
        when(examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                .thenReturn(Collections.singletonList(testQuestion1));
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-1", "exam-1")).thenReturn(Optional.of(testQuestion1));
        when(userRepository.findById("student-1")).thenReturn(Optional.of(testStudent));

        Submission savedSub = Submission.builder()
                .id("sub-legacy")
                .exam(testExam)
                .question(testQuestion1)
                .student(testStudent)
                .status("PENDING")
                .build();

        when(submissionRepository.save(any(Submission.class))).thenReturn(savedSub);
        when(submissionMapper.toDetailResponse(savedSub)).thenReturn(
                SubmissionDetailResponse.builder().id("sub-legacy").status("PENDING").build()
        );

        SubmissionCreateRequest request = SubmissionCreateRequest.builder()
                .sourceCode("public class Legacy {}")
                .questionId(null)
                .build();

        SubmissionDetailResponse response = studentSubmissionService.submitCode("exam-1", "student-1", request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("sub-legacy");

        verify(studentQuestionScoreRepository).upsertIntakeSequence(
                anyString(),
                eq("exam-1"),
                eq("q-1"),
                eq("student-1"),
                eq("sub-legacy")
        );
    }

    @Test
    @DisplayName("Submit Code fails when question does not belong to the exam")
    void testSubmitCode_QuestionDoesNotBelongToExam_ThrowsNotFound() {
        when(examRepository.findByIdForShare("exam-1")).thenReturn(Optional.of(testExam));
        when(examRepository.findByIdAndStudentId("exam-1", "student-1")).thenReturn(Optional.of(testExam));
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("foreign-q", "exam-1")).thenReturn(Optional.empty());

        SubmissionCreateRequest request = SubmissionCreateRequest.builder()
                .sourceCode("public class Test {}")
                .questionId("foreign-q")
                .build();

        assertThatThrownBy(() -> studentSubmissionService.submitCode("exam-1", "student-1", request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Question not found or does not belong to this exam");

        verifyNoInteractions(submissionRepository);
        verifyNoInteractions(studentQuestionScoreRepository);
    }

    @Test
    @DisplayName("Submit Code fails when question is in DRAFT status")
    void testSubmitCode_DraftQuestion_ThrowsIllegalState() {
        when(examRepository.findByIdForShare("exam-1")).thenReturn(Optional.of(testExam));
        when(examRepository.findByIdAndStudentId("exam-1", "student-1")).thenReturn(Optional.of(testExam));
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-2", "exam-1")).thenReturn(Optional.of(testQuestion2));

        SubmissionCreateRequest request = SubmissionCreateRequest.builder()
                .sourceCode("public class Test {}")
                .questionId("q-2")
                .build();

        assertThatThrownBy(() -> studentSubmissionService.submitCode("exam-1", "student-1", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Question is not currently published for submissions");

        verifyNoInteractions(submissionRepository);
        verifyNoInteractions(studentQuestionScoreRepository);
    }

    @Test
    @DisplayName("Submit Code fails when exam is closed or inactive")
    void testSubmitCode_ClosedExam_ThrowsIllegalState() {
        testExam.setStatus("closed");
        when(examRepository.findByIdForShare("exam-1")).thenReturn(Optional.of(testExam));
        when(examRepository.findByIdAndStudentId("exam-1", "student-1")).thenReturn(Optional.of(testExam));

        SubmissionCreateRequest request = SubmissionCreateRequest.builder()
                .sourceCode("public class Test {}")
                .questionId("q-1")
                .build();

        assertThatThrownBy(() -> studentSubmissionService.submitCode("exam-1", "student-1", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Exam is not currently active for submissions");

        verifyNoInteractions(submissionRepository);
        verifyNoInteractions(studentQuestionScoreRepository);
    }
}
