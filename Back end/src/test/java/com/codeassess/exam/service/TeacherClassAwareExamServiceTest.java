package com.codeassess.exam.service;

import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.repository.ClassStudentRepository;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.dto.request.CreateExamDto;
import com.codeassess.exam.dto.request.UpdateExamDto;
import com.codeassess.exam.dto.response.ExamResponse;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TeacherClassAwareExamServiceTest {

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

    @Spy
    private ExamMapper examMapper = new ExamMapper();

    @Spy
    private SubmissionMapper submissionMapper = new SubmissionMapper();

    private TeacherExamService teacherExamService;
    private StudentExamService studentExamService;
    private StudentSubmissionService studentSubmissionService;

    private User teacherA;
    private User teacherB;
    private User student1;

    private CourseClass classA1;
    private CourseClass classA2;
    private CourseClass classB1;

    @BeforeEach
    void setUp() {
        teacherExamService = new TeacherExamService(
                examRepository, examMapper, classStudentRepository, courseClassRepository, submissionRepository
        );
        studentExamService = new StudentExamService(examRepository, examMapper);
        studentSubmissionService = new StudentSubmissionService(
                submissionRepository, submissionMapper, examRepository, userRepository, eventPublisher
        );

        teacherA = User.builder().id("teacher-a").email("teachera@fpt.edu.vn").role(Role.TEACHER).fullName("Teacher A").build();
        teacherB = User.builder().id("teacher-b").email("teacherb@fpt.edu.vn").role(Role.TEACHER).fullName("Teacher B").build();
        student1 = User.builder().id("student-1").email("student1@fpt.edu.vn").role(Role.STUDENT).fullName("Student One").build();

        classA1 = CourseClass.builder().id("class-a1").name("Java Class 1").code("PRJ301-A1").teacher(teacherA).build();
        classA2 = CourseClass.builder().id("class-a2").name("Java Class 2").code("PRJ301-A2").teacher(teacherA).build();
        classB1 = CourseClass.builder().id("class-b1").name("Other Class").code("PRJ301-B1").teacher(teacherB).build();
    }

    @Test
    @DisplayName("Teacher assigns an exam to one eligible class")
    void testTeacherAssignsExamToOneClass() {
        CreateExamDto dto = CreateExamDto.builder()
                .title("Midterm Exam")
                .classIds(List.of("class-a1"))
                .date(LocalDateTime.now().plusDays(1))
                .durationMinutes(90)
                .build();

        when(courseClassRepository.findAllById(List.of("class-a1"))).thenReturn(List.of(classA1));
        when(examRepository.save(any(Exam.class))).thenAnswer(invocation -> {
            Exam e = invocation.getArgument(0);
            e.setId("exam-1");
            return e;
        });

        ExamResponse response = teacherExamService.createExam(dto, "teacher-a");

        assertNotNull(response);
        assertEquals("Midterm Exam", response.getTitle());
        assertEquals("class-a1", response.getClassId());
        assertEquals(List.of("class-a1"), response.getClassIds());
        assertEquals(List.of("PRJ301-A1"), response.getClassCodes());
    }

    @Test
    @DisplayName("Teacher assigns an exam to multiple eligible classes")
    void testTeacherAssignsExamToMultipleClasses() {
        CreateExamDto dto = CreateExamDto.builder()
                .title("Final Exam")
                .classIds(List.of("class-a1", "class-a2"))
                .date(LocalDateTime.now().plusDays(2))
                .durationMinutes(120)
                .build();

        when(courseClassRepository.findAllById(List.of("class-a1", "class-a2"))).thenReturn(List.of(classA1, classA2));
        ArgumentCaptor<Exam> examCaptor = ArgumentCaptor.forClass(Exam.class);
        when(examRepository.save(examCaptor.capture())).thenAnswer(invocation -> {
            Exam e = invocation.getArgument(0);
            e.setId("exam-multi");
            return e;
        });

        ExamResponse response = teacherExamService.createExam(dto, "teacher-a");

        assertNotNull(response);
        Exam savedExam = examCaptor.getValue();
        assertEquals(2, savedExam.getClasses().size());
        assertTrue(savedExam.getClasses().contains(classA1));
        assertTrue(savedExam.getClasses().contains(classA2));

        assertEquals(2, response.getClassIds().size());
        assertTrue(response.getClassCodes().contains("PRJ301-A1"));
        assertTrue(response.getClassCodes().contains("PRJ301-A2"));
    }

    @Test
    @DisplayName("Teacher cannot assign an exam to another teacher's class")
    void testTeacherCannotAssignAnotherTeachersClass() {
        CreateExamDto dto = CreateExamDto.builder()
                .title("Unauthorized Exam")
                .classIds(List.of("class-a1", "class-b1"))
                .date(LocalDateTime.now().plusDays(1))
                .durationMinutes(90)
                .build();

        when(courseClassRepository.findAllById(List.of("class-a1", "class-b1"))).thenReturn(List.of(classA1, classB1));

        assertThrows(ResourceNotFoundException.class, () -> {
            teacherExamService.createExam(dto, "teacher-a");
        });
        verify(examRepository, never()).save(any());
    }

    @Test
    @DisplayName("Duplicate class assignments are handled safely without duplicates")
    void testDuplicateClassAssignmentsAreDeduplicated() {
        CreateExamDto dto = CreateExamDto.builder()
                .title("Exam With Duplicate IDs")
                .classIds(List.of("class-a1", "class-a1", "class-a1"))
                .date(LocalDateTime.now().plusDays(1))
                .durationMinutes(90)
                .build();

        when(courseClassRepository.findAllById(List.of("class-a1"))).thenReturn(List.of(classA1));
        when(examRepository.save(any(Exam.class))).thenAnswer(invocation -> {
            Exam e = invocation.getArgument(0);
            e.setId("exam-dedup");
            return e;
        });

        ExamResponse response = teacherExamService.createExam(dto, "teacher-a");

        assertNotNull(response);
        assertEquals(1, response.getClassIds().size());
        assertEquals("class-a1", response.getClassIds().get(0));
    }

    @Test
    @DisplayName("Student sees an exam assigned to their enrolled class")
    void testStudentSeesExamAssignedToEnrolledClass() {
        Exam exam = Exam.builder()
                .id("exam-1")
                .title("Enrolled Exam")
                .courseClass(classA1)
                .classes(Set.of(classA1))
                .status("active")
                .build();

        when(examRepository.findByStudentId("student-1")).thenReturn(List.of(exam));

        List<ExamResponse> studentExams = studentExamService.getStudentExams("student-1");

        assertEquals(1, studentExams.size());
        assertEquals("exam-1", studentExams.get(0).getId());
        assertEquals("Enrolled Exam", studentExams.get(0).getTitle());
    }

    @Test
    @DisplayName("Student does not see an exam assigned only to unrelated classes")
    void testStudentDoesNotSeeUnrelatedExams() {
        when(examRepository.findByStudentId("student-1")).thenReturn(Collections.emptyList());

        List<ExamResponse> studentExams = studentExamService.getStudentExams("student-1");

        assertTrue(studentExams.isEmpty());
    }

    @Test
    @DisplayName("Student cannot retrieve unauthorized exam details by direct ID")
    void testStudentCannotRetrieveUnauthorizedExamById() {
        when(examRepository.findByIdAndStudentId("unauthorized-exam", "student-1"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            studentExamService.getStudentExamById("unauthorized-exam", "student-1");
        });
    }

    @Test
    @DisplayName("Student cannot submit to an unauthorized exam")
    void testStudentCannotSubmitToUnauthorizedExam() {
        when(examRepository.findByIdAndStudentId("unauthorized-exam", "student-1"))
                .thenReturn(Optional.empty());

        SubmissionCreateRequest request = new SubmissionCreateRequest();
        request.setSourceCode("public class Solution {}");

        assertThrows(ResourceNotFoundException.class, () -> {
            studentSubmissionService.submitCode("unauthorized-exam", "student-1", request);
        });
        verify(submissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Assignment updates do not delete historical submissions or break relationships")
    void testUpdateExamPreservesSubmissions() {
        Exam existingExam = Exam.builder()
                .id("exam-1")
                .title("Midterm v1")
                .courseClass(classA1)
                .classes(new HashSet<>(Set.of(classA1)))
                .status("active")
                .build();

        when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-a"))
                .thenReturn(Optional.of(existingExam));
        when(courseClassRepository.findAllById(List.of("class-a1", "class-a2")))
                .thenReturn(List.of(classA1, classA2));
        when(examRepository.save(any(Exam.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateExamDto updateDto = UpdateExamDto.builder()
                .title("Midterm v2 (Expanded)")
                .classIds(List.of("class-a1", "class-a2"))
                .build();

        ExamResponse updated = teacherExamService.updateExam("exam-1", updateDto, "teacher-a");

        assertEquals("Midterm v2 (Expanded)", updated.getTitle());
        assertEquals(2, updated.getClassIds().size());
        assertTrue(updated.getClassIds().contains("class-a1"));
        assertTrue(updated.getClassIds().contains("class-a2"));
        // Submissions repository was not altered or deleted
        verify(submissionRepository, never()).delete(any());
        verify(submissionRepository, never()).deleteAll(any());
    }
}
