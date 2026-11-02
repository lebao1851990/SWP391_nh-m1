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

    // =========================================================================
    // 1. getAllClasses & getClassById
    // =========================================================================

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
    @DisplayName("getClassById throws 404 when class does not exist")
    void getClassById_ClassNotFound_Throws404() {
        when(courseClassRepository.findById("non-existent")).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                adminClassService.getClassById("non-existent")
        );

        assertThat(ex.getMessage()).isEqualTo("Class not found with id: non-existent");
        verifyNoInteractions(classMapper);
        verifyNoInteractions(classStudentRepository);
    }

    // =========================================================================
    // 2. createClass
    // =========================================================================

    @Test
    @DisplayName("createClass creates class successfully with teacher and studentCount 0")
    void createClass_Success() {
        CreateClassDto dto = CreateClassDto.builder()
                .name("Algorithms & Data Structures")
                .code("CS201")
                .semester("Spring 2027")
                .teacherId("teacher-1")
                .build();

        when(userRepository.findById("teacher-1")).thenReturn(Optional.of(teacherUser));
        when(courseClassRepository.save(any(CourseClass.class))).thenAnswer(inv -> inv.getArgument(0));
        when(classMapper.toResponse(any(CourseClass.class))).thenReturn(
                ClassResponse.builder()
                        .id("class-new")
                        .name("Algorithms & Data Structures")
                        .code("CS201")
                        .semester("Spring 2027")
                        .build()
        );

        ClassResponse result = adminClassService.createClass(dto);

        assertThat(result).isNotNull();
        assertThat(result.getStudentCount()).isEqualTo(0L);

        ArgumentCaptor<CourseClass> captor = ArgumentCaptor.forClass(CourseClass.class);
        verify(courseClassRepository).save(captor.capture());
        CourseClass saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Algorithms & Data Structures");
        assertThat(saved.getCode()).isEqualTo("CS201");
        assertThat(saved.getSemester()).isEqualTo("Spring 2027");
        assertThat(saved.getTeacher()).isEqualTo(teacherUser);
    }

    @Test
    @DisplayName("createClass throws 404 when teacher is not found")
    void createClass_TeacherNotFound_Throws404() {
        CreateClassDto dto = CreateClassDto.builder()
                .name("Algorithms")
                .code("CS201")
                .semester("Spring 2027")
                .teacherId("ghost-teacher")
                .build();

        when(userRepository.findById("ghost-teacher")).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                adminClassService.createClass(dto)
        );

        assertThat(ex.getMessage()).isEqualTo("Teacher not found");
        verify(courseClassRepository, never()).save(any());
        verifyNoInteractions(classMapper);
    }

    @Test
    @DisplayName("createClass throws 400 when user role is not TEACHER")
    void createClass_UserNotTeacherRole_ThrowsBadRequest() {
        CreateClassDto dto = CreateClassDto.builder()
                .name("Algorithms")
                .code("CS201")
                .semester("Spring 2027")
                .teacherId("student-1")
                .build();

        when(userRepository.findById("student-1")).thenReturn(Optional.of(studentUser));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                adminClassService.createClass(dto)
        );

        assertThat(ex.getMessage()).isEqualTo("User is not a teacher");
        verify(courseClassRepository, never()).save(any());
        verifyNoInteractions(classMapper);
    }

    // =========================================================================
    // 3. updateClass
    // =========================================================================

    @Test
    @DisplayName("updateClass updates fields without changing teacher")
    void updateClass_Success_WithoutTeacherReassignment() {
        UpdateClassDto dto = UpdateClassDto.builder()
                .name("Advanced Data Structures")
                .code("CS102-Adv")
                .semester("Spring 2027")
                .teacherId(null)
                .build();

        when(courseClassRepository.findById("class-1")).thenReturn(Optional.of(sampleClass));
        when(courseClassRepository.save(any(CourseClass.class))).thenAnswer(inv -> inv.getArgument(0));
        when(classMapper.toResponse(any(CourseClass.class))).thenReturn(
                ClassResponse.builder()
                        .id("class-1")
                        .name("Advanced Data Structures")
                        .code("CS102-Adv")
                        .semester("Spring 2027")
                        .build()
        );
        when(classStudentRepository.countByCourseClass_Id("class-1")).thenReturn(8L);

        ClassResponse result = adminClassService.updateClass("class-1", dto);

        assertThat(result).isNotNull();
        assertThat(result.getStudentCount()).isEqualTo(8L);
        assertThat(sampleClass.getName()).isEqualTo("Advanced Data Structures");
        assertThat(sampleClass.getCode()).isEqualTo("CS102-Adv");
        assertThat(sampleClass.getSemester()).isEqualTo("Spring 2027");
        assertThat(sampleClass.getTeacher()).isEqualTo(teacherUser); // Unchanged

        verify(userRepository, never()).findById(any());
        verify(courseClassRepository).save(sampleClass);
    }

    @Test
    @DisplayName("updateClass reassigns teacher when valid teacherId provided")
    void updateClass_Success_WithTeacherReassignment() {
        User newTeacher = User.builder()
                .id("teacher-2")
                .email("ada@fpt.edu.vn")
                .fullName("Prof. Ada")
                .role(Role.TEACHER)
                .build();

        UpdateClassDto dto = UpdateClassDto.builder()
                .teacherId("teacher-2")
                .build();

        when(courseClassRepository.findById("class-1")).thenReturn(Optional.of(sampleClass));
        when(userRepository.findById("teacher-2")).thenReturn(Optional.of(newTeacher));
        when(courseClassRepository.save(any(CourseClass.class))).thenAnswer(inv -> inv.getArgument(0));
        when(classMapper.toResponse(any(CourseClass.class))).thenReturn(
                ClassResponse.builder().id("class-1").build()
        );
        when(classStudentRepository.countByCourseClass_Id("class-1")).thenReturn(4L);

        ClassResponse result = adminClassService.updateClass("class-1", dto);

        assertThat(result).isNotNull();
        assertThat(sampleClass.getTeacher()).isEqualTo(newTeacher);
        verify(courseClassRepository).save(sampleClass);
    }

    @Test
    @DisplayName("updateClass throws 404 when class not found")
    void updateClass_ClassNotFound_Throws404() {
        UpdateClassDto dto = UpdateClassDto.builder().name("New Name").build();

        when(courseClassRepository.findById("invalid-class")).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                adminClassService.updateClass("invalid-class", dto)
        );

        assertThat(ex.getMessage()).isEqualTo("Class not found with id: invalid-class");
        verify(courseClassRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateClass throws 404 when updated teacher not found")
    void updateClass_TeacherNotFound_Throws404() {
        UpdateClassDto dto = UpdateClassDto.builder().teacherId("ghost-teacher").build();

        when(courseClassRepository.findById("class-1")).thenReturn(Optional.of(sampleClass));
        when(userRepository.findById("ghost-teacher")).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                adminClassService.updateClass("class-1", dto)
        );

        assertThat(ex.getMessage()).isEqualTo("Teacher not found");
        verify(courseClassRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateClass throws 400 when updated user role is not TEACHER")
    void updateClass_UserNotTeacherRole_ThrowsBadRequest() {
        UpdateClassDto dto = UpdateClassDto.builder().teacherId("student-1").build();

        when(courseClassRepository.findById("class-1")).thenReturn(Optional.of(sampleClass));
        when(userRepository.findById("student-1")).thenReturn(Optional.of(studentUser));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                adminClassService.updateClass("class-1", dto)
        );

        assertThat(ex.getMessage()).isEqualTo("User is not a teacher");
        verify(courseClassRepository, never()).save(any());
    }

    // =========================================================================
    // 4. getClassStudents
    // =========================================================================

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

    // =========================================================================
    // 5. enrollStudent
    // =========================================================================

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
    @DisplayName("enrollStudent throws 400 Bad Request when dto is null")
    void enrollStudent_NullDto_ThrowsBadRequest() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                adminClassService.enrollStudent("class-1", null)
        );

        assertThat(ex.getMessage()).isEqualTo("Either studentId or email must be provided");
        verifyNoInteractions(courseClassRepository);
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
    @DisplayName("enrollStudent throws 404 when student not found by id")
    void enrollStudent_StudentNotFoundById_Throws404() {
        when(courseClassRepository.findById("class-1")).thenReturn(Optional.of(sampleClass));
        when(userRepository.findById("ghost-student")).thenReturn(Optional.empty());

        EnrollStudentDto dto = EnrollStudentDto.builder().studentId("ghost-student").build();

        assertThrows(ResourceNotFoundException.class, () ->
                adminClassService.enrollStudent("class-1", dto)
        );
    }

    @Test
    @DisplayName("enrollStudent throws 404 when student not found by email")
    void enrollStudent_StudentNotFoundByEmail_Throws404() {
        when(courseClassRepository.findById("class-1")).thenReturn(Optional.of(sampleClass));
        when(userRepository.findByEmail("ghost@school.edu")).thenReturn(Optional.empty());

        EnrollStudentDto dto = EnrollStudentDto.builder().studentId(null).email("ghost@school.edu").build();

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                adminClassService.enrollStudent("class-1", dto)
        );

        assertThat(ex.getMessage()).isEqualTo("Student not found with email: ghost@school.edu");
        verify(classStudentRepository, never()).save(any());
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

    // =========================================================================
    // 6. unenrollStudent
    // =========================================================================

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
