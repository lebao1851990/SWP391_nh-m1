package com.codeassess.classes.service;

import com.codeassess.classes.dto.response.ClassResponse;
import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.dto.request.CreateClassDto;
import com.codeassess.classes.dto.request.UpdateClassDto;
import com.codeassess.classes.mapper.ClassMapper;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.user.entity.User;
import com.codeassess.user.entity.Role;
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

    @Transactional(readOnly = true)
    public List<ClassResponse> getAllClasses() {
        return courseClassRepository.findAll().stream()
                .map(classMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ClassResponse getClassById(String id) {
        CourseClass courseClass = courseClassRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found with id: " + id));
        return classMapper.toResponse(courseClass);
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
        
        return classMapper.toResponse(courseClassRepository.save(newClass));
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
        
        return classMapper.toResponse(courseClassRepository.save(courseClass));
    }
}
