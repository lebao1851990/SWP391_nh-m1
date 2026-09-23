package com.codeassess.classes.service;

import com.codeassess.classes.dto.response.ClassResponse;
import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.mapper.ClassMapper;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.common.exception.ResourceNotFoundException;
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

    @Transactional(readOnly = true)
    public List<ClassResponse> getTeacherClasses(String teacherId) {
        return courseClassRepository.findByTeacherId(teacherId).stream()
                .map(classMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ClassResponse getTeacherClassById(String id, String teacherId) {
        CourseClass courseClass = courseClassRepository.findByIdAndTeacherId(id, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found or access denied"));
        return classMapper.toResponse(courseClass);
    }
}
