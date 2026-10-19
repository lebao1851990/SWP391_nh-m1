package com.codeassess.classes.service;

import com.codeassess.classes.dto.request.CreateClassDto;
import com.codeassess.classes.dto.request.EnrollStudentDto;
import com.codeassess.classes.dto.request.UpdateClassDto;
import com.codeassess.classes.dto.response.ClassResponse;
import com.codeassess.classes.dto.response.ClassStudentResponse;
import com.codeassess.classes.entity.ClassStudent;
import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.mapper.ClassMapper;
import com.codeassess.classes.repository.ClassStudentRepository;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.common.exception.ConflictException;
import com.codeassess.common.exception.ResourceNotFoundException;
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
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminClassServiceTest {

    @Mock
    private CourseClassRepository courseClassRepository;

    @Mock
    private ClassMapper classMapper;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClassStudentRepository classStudentRepository;

    @InjectMocks
    private AdminClassService adminClassService;

    private CourseClass sampleClass;
    private User teacherUser;
    private User studentUser;

    @BeforeEach
    void setUp() {
        teacherUser = User.builder()
                .id("teacher-1")
                .email("teacher@school.edu")
                .fullName("Prof. Alan Turing")
                .role(Role.TEACHER)
                .build();

        studentUser = User.builder()
                .id("student-1")
                .email("student@school.edu")
                .fullName("Ada Lovelace")
                .role(Role.STUDENT)
                .build();

        sampleClass = CourseClass.builder()
                .id("class-1")
                .name("Data Structures")
                .code("CS102")
                .semester("Fall 2026")
                .teacher(teacherUser)
                .build();
    }

    @Test
    @DisplayName("getAllClasses returns classes with studentCount populated")
    void getAllClasses_CalculatesStudentCount() {
        when(courseClassRepository.findAll()).thenReturn(List.of(sampleClass));
        when(classMapper.toResponse(sampleClass)).thenReturn(
                ClassResponse.builder().id("class-1").name("Data Structures").build()
        );
        when(classStudentRepository.countByCourseClass_Id("class-1")).thenReturn(5L);

        List<ClassResponse> result = adminClassService.getAllClasses();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStudentCount()).isEqualTo(5L);
        verify(classStudentRepository).countByCourseClass_Id("class-1");
    }

    @Test
    @DisplayName("getClassById returns class with studentCount populated")
    void getClassById_CalculatesStudentCount() {
        when(courseClassRepository.findById("class-1")).thenReturn(Optional.of(sampleClass));
        when(classMapper.toResponse(sampleClass)).thenReturn(
                ClassResponse.builder().id("class-1").name("Data Structures").build()
        );
        when(classStudentRepository.countByCourseClass_Id("class-1")).thenReturn(3L);

        ClassResponse result = adminClassService.getClassById("class-1");

        assertThat(result).isNotNull();
        assertThat(result.getStudentCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("getClassStudents returns list of enrolled students without sensitive fields")
    void getClassStudents_Success() {
        when(courseClassRepository.existsById("class-1")).thenReturn(true);

        ClassStudent cs = ClassStudent.builder()
                .id(new ClassStudent.ClassStudentId("class-1", "student-1"))
                .courseClass(sampleClass)
                .student(studentUser)
                .build();

        when(classStudentRepository.findByCourseClassIdWithStudent("class-1")).thenReturn(List.of(cs));

        List<ClassStudentResponse> result = adminClassService.getClassStudents("class-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("student-1");
        assertThat(result.get(0).getStudentId()).isEqualTo("student-1");
        assertThat(result.get(0).getEmail()).isEqualTo("student@school.edu");
        assertThat(result.get(0).getFullName()).isEqualTo("Ada Lovelace");
    }

    @Test
    @DisplayName("getClassStudents throws 404 when class does not exist")
    void getClassStudents_ClassNotFound_Throws404() {
        when(courseClassRepository.existsById("non-existent")).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () ->
                adminClassService.getClassStudents("non-existent")
        );
    }

    @Test
    @DisplayName("enrollStudent by studentId succeeds")
    void enrollStudent_ById_Success() {
        when(courseClassRepository.findById("class-1")).thenReturn(Optional.of(sampleClass));
        when(userRepository.findById("student-1")).thenReturn(Optional.of(studentUser));
        when(classStudentRepository.existsByClassIdAndStudentId("class-1", "student-1")).thenReturn(false);

        EnrollStudentDto dto = EnrollStudentDto.builder().studentId("student-1").build();

        ClassStudentResponse result = adminClassService.enrollStudent("class-1", dto);

        assertThat(result.getStudentId()).isEqualTo("student-1");
        assertThat(result.getEmail()).isEqualTo("student@school.edu");
        assertThat(result.getFullName()).isEqualTo("Ada Lovelace");

        ArgumentCaptor<ClassStudent> captor = ArgumentCaptor.forClass(ClassStudent.class);
        verify(classStudentRepository).save(captor.capture());
        assertThat(captor.getValue().getCourseClass().getId()).isEqualTo("class-1");
        assertThat(captor.getValue().getStudent().getId()).isEqualTo("student-1");
    }

    @Test
    @DisplayName("enrollStudent by email succeeds")
    void enrollStudent_ByEmail_Success() {
        when(courseClassRepository.findById("class-1")).thenReturn(Optional.of(sampleClass));
        when(userRepository.findByEmail("student@school.edu")).thenReturn(Optional.of(studentUser));
        when(classStudentRepository.existsByClassIdAndStudentId("class-1", "student-1")).thenReturn(false);

        EnrollStudentDto dto = EnrollStudentDto.builder().email("student@school.edu").build();

        ClassStudentResponse result = adminClassService.enrollStudent("class-1", dto);

        assertThat(result.getStudentId()).isEqualTo("student-1");
        assertThat(result.getEmail()).isEqualTo("student@school.edu");
        verify(classStudentRepository).save(any(ClassStudent.class));
    }

    @Test
    @DisplayName("enrollStudent with blank studentId and blank email throws 400 Bad Request")
    void enrollStudent_MissingBoth_ThrowsBadRequest() {
        EnrollStudentDto dto = EnrollStudentDto.builder().studentId("  ").email("").build();

        assertThrows(IllegalArgumentException.class, () ->
                adminClassService.enrollStudent("class-1", dto)
        );
    }

    @Test
    @DisplayName("enrollStudent throws 404 when class not found")
    void enrollStudent_ClassNotFound_Throws404() {
        when(courseClassRepository.findById("invalid-class")).thenReturn(Optional.empty());

        EnrollStudentDto dto = EnrollStudentDto.builder().studentId("student-1").build();

        assertThrows(ResourceNotFoundException.class, () ->
                adminClassService.enrollStudent("invalid-class", dto)
        );
    }

    @Test
    @DisplayName("enrollStudent throws 404 when student not found")
    void enrollStudent_StudentNotFound_Throws404() {
        when(courseClassRepository.findById("class-1")).thenReturn(Optional.of(sampleClass));
        when(userRepository.findById("ghost-student")).thenReturn(Optional.empty());

        EnrollStudentDto dto = EnrollStudentDto.builder().studentId("ghost-student").build();

        assertThrows(ResourceNotFoundException.class, () ->
                adminClassService.enrollStudent("class-1", dto)
        );
    }

    @Test
    @DisplayName("enrollStudent throws 400 Bad Request when user has non-STUDENT role")
    void enrollStudent_UserNotStudentRole_ThrowsBadRequest() {
        when(courseClassRepository.findById("class-1")).thenReturn(Optional.of(sampleClass));
        when(userRepository.findById("teacher-1")).thenReturn(Optional.of(teacherUser));

        EnrollStudentDto dto = EnrollStudentDto.builder().studentId("teacher-1").build();

        assertThrows(IllegalArgumentException.class, () ->
                adminClassService.enrollStudent("class-1", dto)
        );
        verify(classStudentRepository, never()).save(any());
    }

    @Test
    @DisplayName("enrollStudent throws 409 Conflict when student is already enrolled")
    void enrollStudent_DuplicateEnrollment_ThrowsConflict() {
        when(courseClassRepository.findById("class-1")).thenReturn(Optional.of(sampleClass));
        when(userRepository.findById("student-1")).thenReturn(Optional.of(studentUser));
        when(classStudentRepository.existsByClassIdAndStudentId("class-1", "student-1")).thenReturn(true);

        EnrollStudentDto dto = EnrollStudentDto.builder().studentId("student-1").build();

        assertThrows(ConflictException.class, () ->
                adminClassService.enrollStudent("class-1", dto)
        );
        verify(classStudentRepository, never()).save(any());
    }

    @Test
    @DisplayName("unenrollStudent deletes ClassStudent without touching User entity")
    void unenrollStudent_Success() {
        when(courseClassRepository.existsById("class-1")).thenReturn(true);

        ClassStudent cs = ClassStudent.builder()
                .id(new ClassStudent.ClassStudentId("class-1", "student-1"))
                .courseClass(sampleClass)
                .student(studentUser)
                .build();

        when(classStudentRepository.findByClassIdAndStudentId("class-1", "student-1")).thenReturn(Optional.of(cs));

        adminClassService.unenrollStudent("class-1", "student-1");

        verify(classStudentRepository).delete(cs);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("unenrollStudent throws 404 when class does not exist")
    void unenrollStudent_ClassNotFound_Throws404() {
        when(courseClassRepository.existsById("invalid-class")).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () ->
                adminClassService.unenrollStudent("invalid-class", "student-1")
        );
    }

    @Test
    @DisplayName("unenrollStudent throws 404 when student is not enrolled in class")
    void unenrollStudent_NotEnrolled_Throws404() {
        when(courseClassRepository.existsById("class-1")).thenReturn(true);
        when(classStudentRepository.findByClassIdAndStudentId("class-1", "student-1")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                adminClassService.unenrollStudent("class-1", "student-1")
        );
    }
}
