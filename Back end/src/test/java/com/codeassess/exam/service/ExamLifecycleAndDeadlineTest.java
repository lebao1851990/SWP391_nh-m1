package com.codeassess.exam.service;

import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.repository.ClassStudentRepository;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.common.exception.ExamTimeExpiredException;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.mapper.ExamMapper;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.submission.dto.request.SubmissionCreateRequest;
import com.codeassess.submission.dto.response.SubmissionDetailResponse;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.mapper.SubmissionMapper;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.submission.service.StudentSubmissionService;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ExamLifecycleAndDeadlineTest {

    @Mock
    private ExamRepository examRepository;

    @Mock
    private CourseClassRepository courseClassRepository;

    @Mock
    private ClassStudentRepository classStudentRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ExamMapper examMapper;
    private SubmissionMapper submissionMapper;
    private TeacherExamService teacherExamService;
    private AdminExamService adminExamService;
    private StudentSubmissionService studentSubmissionService;

    private User teacher;
    private User student;
    private CourseClass courseClass;
    private Exam activeExam;

    @BeforeEach
    void setUp() {
        examMapper = new ExamMapper();
        submissionMapper = new SubmissionMapper();

        teacherExamService = new TeacherExamService(
                examRepository, examMapper, classStudentRepository,
                courseClassRepository, submissionRepository, userRepository
        );

        adminExamService = new AdminExamService(
                examRepository, examMapper, courseClassRepository, submissionRepository
        );

        studentSubmissionService = new StudentSubmissionService(
                submissionRepository, submissionMapper, examRepository,
                userRepository, eventPublisher, null, null, null, null, null,
                new com.codeassess.submission.config.ZipSubmissionProperties(), null, null, null, null
        );

        teacher = User.builder()
                .id("teacher-uuid-1")
                .email("teacher@codeassess.edu")
                .fullName("Teacher One")
                .role(Role.TEACHER)
                .build();

        student = User.builder()
                .id("student-uuid-1")
                .email("student@codeassess.edu")
                .fullName("Student One")
                .role(Role.STUDENT)
                .build();

        courseClass = CourseClass.builder()
                .id("class-uuid-1")
                .code("CS101")
                .name("Intro to CS")
                .teacher(teacher)
                .build();

        activeExam = Exam.builder()
                .id("exam-uuid-1")
                .title("Midterm Exam")
                .courseClass(courseClass)
                .classes(new HashSet<>(Collections.singletonList(courseClass)))
                .status("active")
                .examDate(LocalDateTime.now().minusMinutes(30))
                .duration(60) // 60 mins -> deadline is 30 mins in future
                .build();
    }

    @Test
    @DisplayName("Case A: Delete exam with 0 submissions performs complete deletion")
    void testDeleteTeacherExam_NoSubmissions_HardDelete() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-uuid-1", "teacher-uuid-1"))
                .thenReturn(Optional.of(activeExam));
        when(submissionRepository.countActiveGradingSubmissionsByExamId("exam-uuid-1"))
                .thenReturn(0L);
        when(submissionRepository.countByExam_Id("exam-uuid-1"))
                .thenReturn(0L);

        teacherExamService.deleteTeacherExam("exam-uuid-1", "teacher-uuid-1");

        verify(examRepository, times(1)).delete(activeExam);
        verify(examRepository, never()).save(any(Exam.class));
    }

    @Test
    @DisplayName("Case B: Delete exam with existing submissions performs soft delete and preserves data")
    void testDeleteTeacherExam_WithSubmissions_SoftDelete() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-uuid-1", "teacher-uuid-1"))
                .thenReturn(Optional.of(activeExam));
        when(submissionRepository.countActiveGradingSubmissionsByExamId("exam-uuid-1"))
                .thenReturn(0L);
        when(submissionRepository.countByExam_Id("exam-uuid-1"))
                .thenReturn(5L); // 5 submissions exist

        teacherExamService.deleteTeacherExam("exam-uuid-1", "teacher-uuid-1");

        assertNotNull(activeExam.getDeletedAt(), "deletedAt must be set");
        assertEquals("archived", activeExam.getStatus(), "Status should be updated to archived");
        verify(examRepository, times(1)).save(activeExam);
        verify(examRepository, never()).delete(any(Exam.class));
    }

    @Test
    @DisplayName("Case C: Cannot delete exam while submissions are actively being graded")
    void testDeleteTeacherExam_WithActiveGrading_ThrowsIllegalStateException() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-uuid-1", "teacher-uuid-1"))
                .thenReturn(Optional.of(activeExam));
        when(submissionRepository.countActiveGradingSubmissionsByExamId("exam-uuid-1"))
                .thenReturn(2L); // 2 in progress

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                teacherExamService.deleteTeacherExam("exam-uuid-1", "teacher-uuid-1")
        );

        assertTrue(ex.getMessage().contains("actively being graded"));
        verify(examRepository, never()).delete(any(Exam.class));
        verify(examRepository, never()).save(any(Exam.class));
    }

    @Test
    @DisplayName("Case D: Unauthorized or non-existent exam delete throws ResourceNotFoundException")
    void testDeleteTeacherExam_NotFoundOrAccessDenied() {
        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-fake", "teacher-uuid-1"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                teacherExamService.deleteTeacherExam("exam-fake", "teacher-uuid-1")
        );
    }

    @Test
    @DisplayName("Admin: Delete exam with submissions performs soft delete")
    void testDeleteAdminExam_WithSubmissions() {
        when(examRepository.findById("exam-uuid-1")).thenReturn(Optional.of(activeExam));
        when(submissionRepository.countActiveGradingSubmissionsByExamId("exam-uuid-1")).thenReturn(0L);
        when(submissionRepository.countByExam_Id("exam-uuid-1")).thenReturn(3L);

        adminExamService.deleteExam("exam-uuid-1");

        assertNotNull(activeExam.getDeletedAt());
        assertEquals("archived", activeExam.getStatus());
        verify(examRepository, times(1)).save(activeExam);
        verify(examRepository, never()).delete(any(Exam.class));
    }

    @Test
    @DisplayName("Auto-lock: Submitting code before deadline succeeds")
    void testSubmitCode_BeforeDeadline_Succeeds() {
        // activeExam started 30 mins ago, duration 60 mins -> deadline in 30 mins
        when(examRepository.findByIdAndStudentId("exam-uuid-1", "student-uuid-1"))
                .thenReturn(Optional.of(activeExam));
        when(userRepository.findById("student-uuid-1"))
                .thenReturn(Optional.of(student));
        when(submissionRepository.save(any(Submission.class)))
                .thenAnswer(inv -> {
                    Submission s = inv.getArgument(0);
                    s.setId("sub-uuid-101");
                    return s;
                });

        SubmissionCreateRequest req = SubmissionCreateRequest.builder()
                .sourceCode("public class Solution { public static void main(String[] args) {} }")
                .build();

        SubmissionDetailResponse res = studentSubmissionService.submitCode("exam-uuid-1", "student-uuid-1", req);

        assertNotNull(res);
        assertEquals("sub-uuid-101", res.getId());
        assertEquals("PENDING", res.getStatus());
    }

    @Test
    @DisplayName("Auto-lock: Submitting code after deadline throws ExamTimeExpiredException (409)")
    void testSubmitCode_AfterDeadline_ThrowsExamTimeExpiredException() {
        // Exam started 120 mins ago, duration 60 mins -> expired 60 mins ago
        Exam expiredExam = Exam.builder()
                .id("exam-expired-1")
                .title("Expired Midterm")
                .courseClass(courseClass)
                .classes(new HashSet<>(Collections.singletonList(courseClass)))
                .status("active")
                .examDate(LocalDateTime.now().minusMinutes(120))
                .duration(60)
                .build();

        when(examRepository.findByIdAndStudentId("exam-expired-1", "student-uuid-1"))
                .thenReturn(Optional.of(expiredExam));

        SubmissionCreateRequest req = SubmissionCreateRequest.builder()
                .sourceCode("public class Solution {}")
                .build();

        ExamTimeExpiredException ex = assertThrows(ExamTimeExpiredException.class, () ->
                studentSubmissionService.submitCode("exam-expired-1", "student-uuid-1", req)
        );

        assertTrue(ex.getMessage().contains("expired"));
        verify(submissionRepository, never()).save(any(Submission.class));
    }

    @Test
    @DisplayName("Auto-lock: Soft-deleted exam rejects submission with ResourceNotFoundException")
    void testSubmitCode_SoftDeletedExam_Rejects() {
        Exam softDeletedExam = Exam.builder()
                .id("exam-deleted-1")
                .title("Deleted Exam")
                .courseClass(courseClass)
                .classes(new HashSet<>(Collections.singletonList(courseClass)))
                .status("archived")
                .deletedAt(LocalDateTime.now().minusMinutes(5))
                .examDate(LocalDateTime.now().minusMinutes(10))
                .duration(60)
                .build();

        when(examRepository.findByIdAndStudentId("exam-deleted-1", "student-uuid-1"))
                .thenReturn(Optional.of(softDeletedExam));

        SubmissionCreateRequest req = SubmissionCreateRequest.builder()
                .sourceCode("public class Solution {}")
                .build();

        assertThrows(ResourceNotFoundException.class, () ->
                studentSubmissionService.submitCode("exam-deleted-1", "student-uuid-1", req)
        );

        verify(submissionRepository, never()).save(any(Submission.class));
    }
}
