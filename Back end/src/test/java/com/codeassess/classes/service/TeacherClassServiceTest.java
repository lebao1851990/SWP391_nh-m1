package com.codeassess.classes.service;

import com.codeassess.classes.dto.request.CreateTeacherClassDto;
import com.codeassess.classes.dto.request.EnrollStudentDto;
import com.codeassess.classes.dto.response.ClassResponse;
import com.codeassess.classes.dto.response.ClassStudentResponse;
import com.codeassess.classes.entity.ClassStudent;
import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.mapper.ClassMapper;
import com.codeassess.classes.repository.ClassStudentRepository;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.common.exception.ConflictException;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeacherClassServiceTest {

    @Mock
    private CourseClassRepository courseClassRepository;

    @Spy
    private ClassMapper classMapper = new ClassMapper();

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClassStudentRepository classStudentRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @InjectMocks
    private TeacherClassService teacherClassService;

    private User teacher;
    private User student;
    private CourseClass courseClass;

    @BeforeEach
    void setUp() {
        teacher = User.builder().id("teacher-1").email("teacher@fpt.edu.vn").role(Role.TEACHER).build();
        student = User.builder().id("student-1").email("student@fpt.edu.vn").fullName("Student One").role(Role.STUDENT).build();
        courseClass = CourseClass.builder()
                .id("class-1")
                .name("PRO192 Java OOP")
                .code("PRO192_SE1701")
                .semester("Fall 2026")
                .teacher(teacher)
                .build();
    }

    @Test
    @DisplayName("getTeacherClasses returns list with student counts")
    void testGetTeacherClasses() {
        when(courseClassRepository.findByTeacherId("teacher-1")).thenReturn(List.of(courseClass));
        when(classStudentRepository.countByCourseClass_Id("class-1")).thenReturn(25L);

        List<ClassResponse> result = teacherClassService.getTeacherClasses("teacher-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("PRO192_SE1701");
        assertThat(result.get(0).getStudentCount()).isEqualTo(25L);
    }

    @Test
    @DisplayName("getTeacherClassById returns class when owned by teacher")
    void testGetTeacherClassById_Success() {
        when(courseClassRepository.findByIdAndTeacherId("class-1", "teacher-1")).thenReturn(Optional.of(courseClass));
        when(classStudentRepository.countByCourseClass_Id("class-1")).thenReturn(30L);

        ClassResponse result = teacherClassService.getTeacherClassById("class-1", "teacher-1");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("PRO192 Java OOP");
        assertThat(result.getStudentCount()).isEqualTo(30L);
    }

    @Test
    @DisplayName("getTeacherClassById throws 404 when not found or owned by other teacher")
    void testGetTeacherClassById_NotFound() {
        when(courseClassRepository.findByIdAndTeacherId("class-1", "other-teacher")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherClassService.getTeacherClassById("class-1", "other-teacher"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found or access denied");
    }

    @Test
    @DisplayName("createTeacherClass persists new class and defaults semester")
    void testCreateTeacherClass_Success() {
        CreateTeacherClassDto dto = new CreateTeacherClassDto();
        dto.setName("Data Structures");
        dto.setCode("CSD201");
        dto.setSemester(null); // Defaults to Fall 2026

        when(courseClassRepository.findByCode("CSD201")).thenReturn(Optional.empty());
        when(userRepository.findById("teacher-1")).thenReturn(Optional.of(teacher));
        when(courseClassRepository.save(any(CourseClass.class))).thenAnswer(inv -> {
            CourseClass c = inv.getArgument(0);
            c.setId("class-created");
            return c;
        });

        ClassResponse result = teacherClassService.createTeacherClass(dto, "teacher-1");

        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo("CSD201");
        assertThat(result.getSemester()).isEqualTo("Fall 2026");
        assertThat(result.getStudentCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("createTeacherClass throws ConflictException if class code already exists")
    void testCreateTeacherClass_DuplicateCode() {
        CreateTeacherClassDto dto = new CreateTeacherClassDto();
        dto.setName("Duplicate Class");
        dto.setCode("PRO192_SE1701");

        when(courseClassRepository.findByCode("PRO192_SE1701")).thenReturn(Optional.of(courseClass));

        assertThatThrownBy(() -> teacherClassService.createTeacherClass(dto, "teacher-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("getClassStudents returns enrolled students")
    void testGetClassStudents() {
        ClassStudent.ClassStudentId csId = new ClassStudent.ClassStudentId("class-1", "student-1");
        ClassStudent cs = ClassStudent.builder().id(csId).courseClass(courseClass).student(student).build();
        when(courseClassRepository.findByIdAndTeacherId("class-1", "teacher-1")).thenReturn(Optional.of(courseClass));
        when(classStudentRepository.findByCourseClassIdWithStudent("class-1")).thenReturn(List.of(cs));

        List<ClassStudentResponse> students = teacherClassService.getClassStudents("class-1", "teacher-1");

        assertThat(students).hasSize(1);
        assertThat(students.get(0).getEmail()).isEqualTo("student@fpt.edu.vn");
        assertThat(students.get(0).getFullName()).isEqualTo("Student One");
    }

    @Test
    @DisplayName("enrollStudent enrolls student by studentId")
    void testEnrollStudent_ByStudentId() {
        EnrollStudentDto dto = new EnrollStudentDto();
        dto.setStudentId("student-1");

        when(courseClassRepository.findByIdAndTeacherId("class-1", "teacher-1")).thenReturn(Optional.of(courseClass));
        when(userRepository.findById("student-1")).thenReturn(Optional.of(student));
        when(classStudentRepository.existsByClassIdAndStudentId("class-1", "student-1")).thenReturn(false);
        when(classStudentRepository.save(any(ClassStudent.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassStudentResponse result = teacherClassService.enrollStudent("class-1", dto, "teacher-1");

        assertThat(result.getStudentId()).isEqualTo("student-1");
        assertThat(result.getEmail()).isEqualTo("student@fpt.edu.vn");
    }

    @Test
    @DisplayName("enrollStudent throws ConflictException if already enrolled")
    void testEnrollStudent_AlreadyEnrolled() {
        EnrollStudentDto dto = new EnrollStudentDto();
        dto.setStudentId("student-1");

        when(courseClassRepository.findByIdAndTeacherId("class-1", "teacher-1")).thenReturn(Optional.of(courseClass));
        when(userRepository.findById("student-1")).thenReturn(Optional.of(student));
        when(classStudentRepository.existsByClassIdAndStudentId("class-1", "student-1")).thenReturn(true);

        assertThatThrownBy(() -> teacherClassService.enrollStudent("class-1", dto, "teacher-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Sinh viên đã có trong danh sách lớp học");
    }

    @Test
    @DisplayName("unenrollStudent succeeds when no submissions exist")
    void testUnenrollStudent_Success() {
        ClassStudent.ClassStudentId csId = new ClassStudent.ClassStudentId("class-1", "student-1");
        ClassStudent cs = ClassStudent.builder().id(csId).courseClass(courseClass).student(student).build();
        when(courseClassRepository.findByIdAndTeacherId("class-1", "teacher-1")).thenReturn(Optional.of(courseClass));
        when(submissionRepository.existsByStudent_IdAndExam_CourseClass_Id("student-1", "class-1")).thenReturn(false);
        when(classStudentRepository.findByClassIdAndStudentId("class-1", "student-1")).thenReturn(Optional.of(cs));

        teacherClassService.unenrollStudent("class-1", "student-1", "teacher-1");

        verify(classStudentRepository).delete(cs);
    }
}

