package com.codeassess.exam.mapper;

import com.codeassess.exam.dto.response.ExamResponse;
import com.codeassess.exam.entity.Exam;
import org.springframework.stereotype.Component;

@Component
public class ExamMapper {

    public ExamResponse toResponse(Exam exam) {
        if (exam == null) {
            return null;
        }

        return ExamResponse.builder()
                .id(exam.getId())
                .classId(exam.getCourseClass() != null ? exam.getCourseClass().getId() : null)
                .title(exam.getTitle())
                .examDate(exam.getExamDate())
                .duration(exam.getDuration())
                .status(exam.getStatus())
                .createdAt(exam.getCreatedAt())
                .courseName(exam.getCourseClass() != null ? exam.getCourseClass().getName() : null)
                .courseCode(exam.getCourseClass() != null ? exam.getCourseClass().getCode() : null)
                .build();
    }
}
