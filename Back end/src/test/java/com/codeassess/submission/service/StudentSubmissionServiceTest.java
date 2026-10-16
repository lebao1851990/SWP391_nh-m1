package com.codeassess.submission.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.submission.dto.request.SubmissionCreateRequest;
import com.codeassess.submission.dto.response.SubmissionDetailResponse;
import com.codeassess.submission.dto.response.SubmissionListResponse;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.event.SubmissionCreatedEvent;
import com.codeassess.submission.mapper.SubmissionMapper;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentSubmissionServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private ExamRepository examRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final SubmissionMapper submissionMapper = new SubmissionMapper();

    private StudentSubmissionService service;

    @BeforeEach
    void setUp() {
        service = new StudentSubmissionService(
                submissionRepository,
                submissionMapper,
                examRepository,
                userRepository,
                eventPublisher
        );
    }

    @Test
    void submitCode_whenExamIsInactive_throwsIllegalStateException() {
        Exam exam = new Exam();
        exam.setId("exam-1");
        exam.setStatus("INACTIVE");
        when(examRepository.findByIdAndStudentId("exam-1", "student-1")).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.submitCode("exam-1", "student-1", new SubmissionCreateRequest("class Demo {}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not currently active");

        verify(submissionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void submitCode_whenStudentNotEnrolled_throwsResourceNotFound() {
        when(examRepository.findByIdAndStudentId("exam-1", "student-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitCode("exam-1", "student-1", new SubmissionCreateRequest("class Demo {}")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not enrolled");

        verify(submissionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void submitCode_whenStudentMissing_throwsResourceNotFound() {
        Exam exam = new Exam();
        exam.setId("exam-1");
        exam.setStatus("active");
        when(examRepository.findByIdAndStudentId("exam-1", "student-1")).thenReturn(Optional.of(exam));
        when(userRepository.findById("student-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitCode("exam-1", "student-1", new SubmissionCreateRequest("class Demo {}")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Student not found");

        verify(submissionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void getStudentSubmissionById_whenMissing_throwsResourceNotFound() {
        when(submissionRepository.findByIdAndStudent_Id("sub-missing", "student-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStudentSubmissionById("sub-missing", "student-1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("access denied");
    }

    @Test
    void getStudentSubmissions_returnsMappedList() {
        Exam exam = new Exam();
        exam.setId("exam-1");
        User student = new User();
        student.setId("student-1");
        Submission submission = Submission.builder()
                .id("sub-1")
                .exam(exam)
                .student(student)
                .status("PENDING")
                .build();
        when(submissionRepository.findByStudent_Id("student-1")).thenReturn(List.of(submission));

        List<SubmissionListResponse> result = service.getStudentSubmissions("student-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("sub-1");
        assertThat(result.get(0).getExamId()).isEqualTo("exam-1");
        assertThat(result.get(0).getStudentId()).isEqualTo("student-1");
        assertThat(result.get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void submitCode_whenValid_publishesEventAndReturnsSubmission() {
        Exam exam = new Exam();
        exam.setId("exam-1");
        exam.setStatus("active");
        User student = new User();
        student.setId("student-1");

        when(examRepository.findByIdAndStudentId("exam-1", "student-1")).thenReturn(Optional.of(exam));
        when(userRepository.findById("student-1")).thenReturn(Optional.of(student));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> {
            Submission saved = invocation.getArgument(0);
            saved.setId("sub-1");
            return saved;
        });

        SubmissionDetailResponse result = service.submitCode("exam-1", "student-1", new SubmissionCreateRequest("class Demo {}"));

        assertThat(result.getId()).isEqualTo("sub-1");
        assertThat(result.getExamId()).isEqualTo("exam-1");
        assertThat(result.getStudentId()).isEqualTo("student-1");
        assertThat(result.getSourceCode()).isEqualTo("class Demo {}");
        assertThat(result.getStatus()).isEqualTo("PENDING");

        ArgumentCaptor<Submission> savedCaptor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(savedCaptor.capture());
        Submission savedSubmission = savedCaptor.getValue();
        assertThat(savedSubmission.getExam()).isSameAs(exam);
        assertThat(savedSubmission.getStudent()).isSameAs(student);
        assertThat(savedSubmission.getSourceCode()).isEqualTo("class Demo {}");
        assertThat(savedSubmission.getStatus()).isEqualTo("PENDING");

        ArgumentCaptor<SubmissionCreatedEvent> eventCaptor = ArgumentCaptor.forClass(SubmissionCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getExamId()).isEqualTo("exam-1");
        assertThat(eventCaptor.getValue().getSubmissionId()).isEqualTo("sub-1");
    }
}
