package com.codeassess.submission.service;

import com.codeassess.classes.entity.CourseClass;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.submission.dto.response.SubmissionDetailResponse;
import com.codeassess.submission.dto.response.SubmissionFileDto;
import com.codeassess.submission.dto.response.TeacherSubmissionListResponse;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.mapper.SubmissionMapper;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeacherSubmissionServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Spy
    private SubmissionMapper submissionMapper = new SubmissionMapper();

    @Mock
    private SubmissionStorageService submissionStorageService;

    @InjectMocks
    private TeacherSubmissionService service;

    private User teacher;
    private User student;
    private CourseClass courseClass;
    private Exam exam;
    private Submission submission;

    @BeforeEach
    void setUp() {
        teacher = User.builder().id("teacher-1").fullName("Teacher One").email("teacher@fpt.edu.vn").build();
        student = User.builder().id("student-1").fullName("Student One").email("student@fpt.edu.vn").build();
        courseClass = CourseClass.builder().id("class-1").code("PRO192").name("Java OOP").teacher(teacher).build();
        exam = Exam.builder().id("exam-1").title("Midterm OOP").courseClass(courseClass).build();

        submission = Submission.builder()
                .id("sub-1")
                .exam(exam)
                .student(student)
                .status("COMPLETED")
                .totalScore(BigDecimal.valueOf(8.5))
                .autoScore(BigDecimal.valueOf(7.0))
                .testCasesPassed(7)
                .testCasesTotal(10)
                .executionStatus("SUCCESS")
                .originalFilename("Solution.zip")
                .createdAt(LocalDateTime.now().minusHours(2))
                .evaluatedAt(LocalDateTime.now().minusHours(1))
                .instructorFeedback("Good work on OOP principles")
                .build();
    }

    @Test
    @DisplayName("getTeacherSubmissions returns mapped responses with evaluatedAt when status is COMPLETED")
    void getTeacherSubmissions_successWithCompletedStatus() {
        when(submissionRepository.findByExam_CourseClass_Teacher_Id("teacher-1")).thenReturn(List.of(submission));

        List<TeacherSubmissionListResponse> result = service.getTeacherSubmissions("teacher-1");

        assertThat(result).hasSize(1);
        TeacherSubmissionListResponse resp = result.get(0);
        assertThat(resp.getId()).isEqualTo("sub-1");
        assertThat(resp.getStudentName()).isEqualTo("Student One");
        assertThat(resp.getStudentEmail()).isEqualTo("student@fpt.edu.vn");
        assertThat(resp.getExamTitle()).isEqualTo("Midterm OOP");
        assertThat(resp.getCourseCode()).isEqualTo("PRO192");
        assertThat(resp.getStatus()).isEqualTo("COMPLETED");
        assertThat(resp.getTotalScore()).isEqualByComparingTo("8.5");
        assertThat(resp.getAutoScore()).isEqualByComparingTo("7.0");
        assertThat(resp.getTestCasesPassed()).isEqualTo(7);
        assertThat(resp.getTestCasesTotal()).isEqualTo(10);
        assertThat(resp.getSubmittedAt()).isEqualTo(submission.getEvaluatedAt());
        assertThat(resp.getInstructorFeedback()).isEqualTo("Good work on OOP principles");
    }

    @Test
    @DisplayName("getTeacherSubmissions handles null student, exam, courseClass, and fallback to createdAt")
    void getTeacherSubmissions_withNullFieldsAndCreatedAtFallback() {
        Submission partialSub = Submission.builder()
                .id("sub-2")
                .student(null)
                .exam(null)
                .status("PENDING")
                .createdAt(LocalDateTime.now().minusMinutes(30))
                .evaluatedAt(null)
                .build();

        when(submissionRepository.findByExam_CourseClass_Teacher_Id("teacher-1")).thenReturn(List.of(partialSub));

        List<TeacherSubmissionListResponse> result = service.getTeacherSubmissions("teacher-1");

        assertThat(result).hasSize(1);
        TeacherSubmissionListResponse resp = result.get(0);
        assertThat(resp.getId()).isEqualTo("sub-2");
        assertThat(resp.getStudentName()).isNull();
        assertThat(resp.getStudentEmail()).isNull();
        assertThat(resp.getExamTitle()).isNull();
        assertThat(resp.getCourseCode()).isNull();
        assertThat(resp.getSubmittedAt()).isEqualTo(partialSub.getCreatedAt());
    }

    @Test
    @DisplayName("getTeacherSubmissions returns empty list when teacher has no submissions")
    void getTeacherSubmissions_emptyList() {
        when(submissionRepository.findByExam_CourseClass_Teacher_Id("teacher-empty")).thenReturn(Collections.emptyList());

        List<TeacherSubmissionListResponse> result = service.getTeacherSubmissions("teacher-empty");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getTeacherSubmissionById returns detail response when submission belongs to teacher")
    void getTeacherSubmissionById_successWhenOwned() {
        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1"))
                .thenReturn(Optional.of(submission));

        SubmissionDetailResponse detail = service.getTeacherSubmissionById("sub-1", "teacher-1");

        assertThat(detail).isNotNull();
        assertThat(detail.getId()).isEqualTo("sub-1");
    }

    @Test
    @DisplayName("getTeacherSubmissionById throws ResourceNotFoundException when not owned by teacher or missing")
    void getTeacherSubmissionById_throwsWhenNotOwnedOrNotFound() {
        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "other-teacher"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTeacherSubmissionById("sub-1", "other-teacher"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Submission not found or access denied");
    }

    @Test
    @DisplayName("getSubmissionZip returns zip bytes when storage is available and teacher owns submission")
    void getSubmissionZip_success() {
        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1"))
                .thenReturn(Optional.of(submission));
        byte[] expected = "zip-data".getBytes();
        when(submissionStorageService.getZipBytes("sub-1")).thenReturn(expected);

        byte[] actual = service.getSubmissionZip("sub-1", "teacher-1");

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("getSubmissionZip throws ResourceNotFoundException when storage service is null")
    void getSubmissionZip_storageNull_throwsResourceNotFound() {
        TeacherSubmissionService serviceWithoutStorage = new TeacherSubmissionService(submissionRepository, submissionMapper, null);
        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1"))
                .thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> serviceWithoutStorage.getSubmissionZip("sub-1", "teacher-1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Storage service not available");
    }

    @Test
    @DisplayName("getSubmissionZip throws ResourceNotFoundException when submission not owned by teacher")
    void getSubmissionZip_notOwned_throwsResourceNotFound() {
        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "other-teacher"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSubmissionZip("sub-1", "other-teacher"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Submission not found or access denied");

        verifyNoInteractions(submissionStorageService);
    }

    @Test
    @DisplayName("getSubmissionFiles returns files when storage available and owned by teacher")
    void getSubmissionFiles_success() {
        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1"))
                .thenReturn(Optional.of(submission));
        SubmissionFileDto fileDto = SubmissionFileDto.builder()
                .filename("Main.java")
                .relativePath("src/Main.java")
                .sizeBytes(120L)
                .content("public class Main {}")
                .build();
        when(submissionStorageService.getSubmissionFiles("sub-1")).thenReturn(List.of(fileDto));

        List<SubmissionFileDto> files = service.getSubmissionFiles("sub-1", "teacher-1");

        assertThat(files).hasSize(1);
        assertThat(files.get(0).getFilename()).isEqualTo("Main.java");
    }

    @Test
    @DisplayName("getSubmissionFiles returns empty list when storage service is null")
    void getSubmissionFiles_storageNull_returnsEmptyList() {
        TeacherSubmissionService serviceWithoutStorage = new TeacherSubmissionService(submissionRepository, submissionMapper, null);
        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1"))
                .thenReturn(Optional.of(submission));

        List<SubmissionFileDto> files = serviceWithoutStorage.getSubmissionFiles("sub-1", "teacher-1");

        assertThat(files).isEmpty();
    }

    @Test
    @DisplayName("getSubmissionFiles throws ResourceNotFoundException when not owned by teacher")
    void getSubmissionFiles_notOwned_throwsResourceNotFound() {
        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "other-teacher"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSubmissionFiles("sub-1", "other-teacher"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Submission not found or access denied");

        verifyNoInteractions(submissionStorageService);
    }

    @Test
    @DisplayName("saveInstructorFeedback updates feedback and saves submission when owned by teacher")
    void saveInstructorFeedback_success() {
        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "teacher-1"))
                .thenReturn(Optional.of(submission));

        service.saveInstructorFeedback("sub-1", "teacher-1", "Revised feedback: Outstanding execution.");

        assertThat(submission.getInstructorFeedback()).isEqualTo("Revised feedback: Outstanding execution.");
        verify(submissionRepository).save(submission);
    }

    @Test
    @DisplayName("saveInstructorFeedback throws ResourceNotFoundException and does not save when not owned by teacher")
    void saveInstructorFeedback_notOwned_throwsResourceNotFound() {
        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-1", "other-teacher"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveInstructorFeedback("sub-1", "other-teacher", "Malicious feedback"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Submission not found or access denied");

        verify(submissionRepository, never()).save(any());
    }
}

