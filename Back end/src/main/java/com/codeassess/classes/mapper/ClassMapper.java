package com.codeassess.classes.mapper;

import com.codeassess.classes.dto.response.ClassResponse;
import com.codeassess.classes.entity.CourseClass;
import org.springframework.stereotype.Component;

@Component
public class ClassMapper {

    public ClassResponse toResponse(CourseClass courseClass) {
        if (courseClass == null) {
            return null;
        }

        return ClassResponse.builder()
                .id(courseClass.getId())
                .name(courseClass.getName())
                .code(courseClass.getCode())
                .semester(courseClass.getSemester())
                .teacherId(courseClass.getTeacher() != null ? courseClass.getTeacher().getId() : null)
                .createdAt(courseClass.getCreatedAt())
                .build();
    }
}
