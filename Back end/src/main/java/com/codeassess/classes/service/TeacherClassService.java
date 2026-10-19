package com.codeassess.classes.service;

import com.codeassess.classes.dto.request.CreateTeacherClassDto;
import com.codeassess.classes.dto.response.ClassResponse;
import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.mapper.ClassMapper;
import com.codeassess.classes.repository.ClassStudentRepository;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.common.exception.ConflictException;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherClassService {

    private final CourseClassRepository courseClassRepository;
    private final ClassMapper classMapper;
    private final UserRepository userRepository;
    private final ClassStudentRepository classStudentRepository;
    private final com.codeassess.submission.repository.SubmissionRepository submissionRepository;

    @Transactional(readOnly = true)
    public List<ClassResponse> getTeacherClasses(String teacherId) {
        return courseClassRepository.findByTeacherId(teacherId).stream()
                .map(courseClass -> {
                    ClassResponse resp = classMapper.toResponse(courseClass);
                    resp.setStudentCount(classStudentRepository.countByCourseClass_Id(courseClass.getId()));
                    return resp;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ClassResponse getTeacherClassById(String id, String teacherId) {
        CourseClass courseClass = courseClassRepository.findByIdAndTeacherId(id, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found or access denied"));
        ClassResponse resp = classMapper.toResponse(courseClass);
        resp.setStudentCount(classStudentRepository.countByCourseClass_Id(courseClass.getId()));
        return resp;
    }

    @Transactional
    public ClassResponse createTeacherClass(CreateTeacherClassDto dto, String teacherId) {
        String code = dto.getCode().trim().toUpperCase();
        if (courseClassRepository.findByCode(code).isPresent()) {
            throw new ConflictException("Course class with code '" + code + "' already exists");
        }

        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with id: " + teacherId));

        CourseClass newClass = CourseClass.builder()
                .name(dto.getName().trim())
                .code(code)
                .semester(dto.getSemester() != null && !dto.getSemester().isBlank() ? dto.getSemester().trim() : "Fall 2026")
                .teacher(teacher)
                .build();

        CourseClass saved = courseClassRepository.save(newClass);
        ClassResponse resp = classMapper.toResponse(saved);
        resp.setStudentCount(0L);
        return resp;
    }

    @Transactional(readOnly = true)
    public List<com.codeassess.classes.dto.response.ClassStudentResponse> getClassStudents(String classId, String teacherId) {
        CourseClass courseClass = courseClassRepository.findByIdAndTeacherId(classId, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found or access denied"));

        return classStudentRepository.findByCourseClassIdWithStudent(courseClass.getId()).stream()
                .map(cs -> com.codeassess.classes.dto.response.ClassStudentResponse.builder()
                        .id(cs.getStudent().getId())
                        .studentId(cs.getStudent().getId())
                        .email(cs.getStudent().getEmail())
                        .fullName(cs.getStudent().getFullName())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public com.codeassess.classes.dto.response.ClassStudentResponse enrollStudent(
            String classId, com.codeassess.classes.dto.request.EnrollStudentDto dto, String teacherId) {
        CourseClass courseClass = courseClassRepository.findByIdAndTeacherId(classId, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found or access denied"));

        if (dto == null || ((dto.getStudentId() == null || dto.getStudentId().isBlank())
                && (dto.getEmail() == null || dto.getEmail().isBlank()))) {
            throw new IllegalArgumentException("Either studentId or email must be provided");
        }

        User student;
        if (dto.getStudentId() != null && !dto.getStudentId().isBlank()) {
            student = userRepository.findById(dto.getStudentId().trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + dto.getStudentId()));
        } else {
            student = userRepository.findByEmail(dto.getEmail().trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Student not found with email: " + dto.getEmail()));
        }

        if (student.getRole() != com.codeassess.user.entity.Role.STUDENT) {
            throw new IllegalArgumentException("User is not a student (role: " + student.getRole() + ")");
        }

        if (classStudentRepository.existsByClassIdAndStudentId(classId, student.getId())) {
            throw new ConflictException("Sinh viên đã có trong danh sách lớp học");
        }

        com.codeassess.classes.entity.ClassStudent.ClassStudentId csId =
                new com.codeassess.classes.entity.ClassStudent.ClassStudentId(courseClass.getId(), student.getId());
        com.codeassess.classes.entity.ClassStudent classStudent = com.codeassess.classes.entity.ClassStudent.builder()
                .id(csId)
                .courseClass(courseClass)
                .student(student)
                .build();

        classStudentRepository.save(classStudent);

        return com.codeassess.classes.dto.response.ClassStudentResponse.builder()
                .id(student.getId())
                .studentId(student.getId())
                .email(student.getEmail())
                .fullName(student.getFullName())
                .build();
    }

    @Transactional
    public void unenrollStudent(String classId, String studentId, String teacherId) {
        CourseClass courseClass = courseClassRepository.findByIdAndTeacherId(classId, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found or access denied"));

        if (submissionRepository.existsByStudent_IdAndExam_CourseClass_Id(studentId, courseClass.getId())) {
            throw new ConflictException("Không thể xóa sinh viên khỏi lớp vì sinh viên đã nộp bài thi trong lớp học này.");
        }

        com.codeassess.classes.entity.ClassStudent classStudent = classStudentRepository
                .findByClassIdAndStudentId(courseClass.getId(), studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student enrollment not found in this class"));

        classStudentRepository.delete(classStudent);
    }
}

