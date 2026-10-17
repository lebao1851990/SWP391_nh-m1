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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminClassService {

    private final CourseClassRepository courseClassRepository;
    private final ClassMapper classMapper;
    private final UserRepository userRepository;
    private final ClassStudentRepository classStudentRepository;

    @Transactional(readOnly = true)
    public List<ClassResponse> getAllClasses() {
        return courseClassRepository.findAll().stream()
                .map(courseClass -> {
                    ClassResponse response = classMapper.toResponse(courseClass);
                    response.setStudentCount(classStudentRepository.countByCourseClass_Id(courseClass.getId()));
                    return response;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ClassResponse getClassById(String id) {
        CourseClass courseClass = courseClassRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found with id: " + id));
        ClassResponse response = classMapper.toResponse(courseClass);
        response.setStudentCount(classStudentRepository.countByCourseClass_Id(courseClass.getId()));
        return response;
    }

    @Transactional
    public ClassResponse createClass(CreateClassDto dto) {
        User teacher = userRepository.findById(dto.getTeacherId())
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found"));
        
        if (teacher.getRole() != Role.TEACHER) {
            throw new IllegalArgumentException("User is not a teacher");
        }

        CourseClass newClass = CourseClass.builder()
                .name(dto.getName())
                .code(dto.getCode())
                .semester(dto.getSemester())
                .teacher(teacher)
                .build();
        
        ClassResponse response = classMapper.toResponse(courseClassRepository.save(newClass));
        response.setStudentCount(0L);
        return response;
    }

    @Transactional
    public ClassResponse updateClass(String id, UpdateClassDto dto) {
        CourseClass courseClass = courseClassRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found with id: " + id));
        
        if (dto.getName() != null) courseClass.setName(dto.getName());
        if (dto.getCode() != null) courseClass.setCode(dto.getCode());
        if (dto.getSemester() != null) courseClass.setSemester(dto.getSemester());
        
        if (dto.getTeacherId() != null) {
            User teacher = userRepository.findById(dto.getTeacherId())
                    .orElseThrow(() -> new ResourceNotFoundException("Teacher not found"));
            if (teacher.getRole() != Role.TEACHER) {
                throw new IllegalArgumentException("User is not a teacher");
            }
            courseClass.setTeacher(teacher);
        }
        
        ClassResponse response = classMapper.toResponse(courseClassRepository.save(courseClass));
        response.setStudentCount(classStudentRepository.countByCourseClass_Id(courseClass.getId()));
        return response;
    }

    @Transactional(readOnly = true)
    public List<ClassStudentResponse> getClassStudents(String classId) {
        if (!courseClassRepository.existsById(classId)) {
            throw new ResourceNotFoundException("Class not found with id: " + classId);
        }

        return classStudentRepository.findByCourseClassIdWithStudent(classId).stream()
                .map(cs -> ClassStudentResponse.builder()
                        .id(cs.getStudent().getId())
                        .studentId(cs.getStudent().getId())
                        .email(cs.getStudent().getEmail())
                        .fullName(cs.getStudent().getFullName())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public ClassStudentResponse enrollStudent(String classId, EnrollStudentDto dto) {
        if (dto == null || ((dto.getStudentId() == null || dto.getStudentId().isBlank()) && (dto.getEmail() == null || dto.getEmail().isBlank()))) {
            throw new IllegalArgumentException("Either studentId or email must be provided");
        }

        CourseClass courseClass = courseClassRepository.findById(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found with id: " + classId));

        User student;
        if (dto.getStudentId() != null && !dto.getStudentId().isBlank()) {
            student = userRepository.findById(dto.getStudentId().trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + dto.getStudentId()));
        } else {
            student = userRepository.findByEmail(dto.getEmail().trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Student not found with email: " + dto.getEmail()));
        }

        if (student.getRole() != Role.STUDENT) {
            throw new IllegalArgumentException("User is not a student (role: " + student.getRole() + ")");
        }

        if (classStudentRepository.existsByClassIdAndStudentId(classId, student.getId())) {
            throw new ConflictException("Student is already enrolled in this class");
        }

        ClassStudent.ClassStudentId csId = new ClassStudent.ClassStudentId(courseClass.getId(), student.getId());
        ClassStudent classStudent = ClassStudent.builder()
                .id(csId)
                .courseClass(courseClass)
                .student(student)
                .build();

        classStudentRepository.save(classStudent);

        return ClassStudentResponse.builder()
                .id(student.getId())
                .studentId(student.getId())
                .email(student.getEmail())
                .fullName(student.getFullName())
                .build();
    }

    @Transactional
    public void unenrollStudent(String classId, String studentId) {
        if (!courseClassRepository.existsById(classId)) {
            throw new ResourceNotFoundException("Class not found with id: " + classId);
        }

        ClassStudent classStudent = classStudentRepository.findByClassIdAndStudentId(classId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student enrollment not found in this class"));

        classStudentRepository.delete(classStudent);
    }
}
