package com.codeassess.exam.mapper;

import com.codeassess.classes.entity.CourseClass;
import com.codeassess.exam.dto.response.ClassSummaryDto;
import com.codeassess.exam.dto.response.ExamResponse;
import com.codeassess.exam.entity.Exam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ExamMapper {

    public ExamResponse toResponse(Exam exam) {
        if (exam == null) {
            return null;
        }

        List<ClassSummaryDto> assignedClasses = new ArrayList<>();
        if (exam.getClasses() != null && !exam.getClasses().isEmpty()) {
            assignedClasses = exam.getClasses().stream()
                    .map(c -> ClassSummaryDto.builder()
                            .id(c.getId())
                            .name(c.getName())
                            .code(c.getCode())
                            .semester(c.getSemester())
                            .build())
                    .sorted(Comparator.comparing(ClassSummaryDto::getCode, Comparator.nullsLast(String::compareTo)))
                    .collect(Collectors.toList());
        } else if (exam.getCourseClass() != null) {
            CourseClass c = exam.getCourseClass();
            assignedClasses.add(ClassSummaryDto.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .code(c.getCode())
                    .semester(c.getSemester())
                    .build());
        }

        List<String> classIds = assignedClasses.stream().map(ClassSummaryDto::getId).collect(Collectors.toList());
        List<String> classCodes = assignedClasses.stream().map(ClassSummaryDto::getCode).collect(Collectors.toList());
        List<String> classNames = assignedClasses.stream().map(ClassSummaryDto::getName).collect(Collectors.toList());

        String primaryClassId = !classIds.isEmpty() ? classIds.get(0) : (exam.getCourseClass() != null ? exam.getCourseClass().getId() : null);
        String primaryCourseName = !classNames.isEmpty() ? String.join(", ", classNames) : (exam.getCourseClass() != null ? exam.getCourseClass().getName() : null);
        String primaryCourseCode = !classCodes.isEmpty() ? String.join(", ", classCodes) : (exam.getCourseClass() != null ? exam.getCourseClass().getCode() : null);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime = exam.getExamDate() != null && exam.getDuration() != null && exam.getDuration() > 0
                ? exam.getExamDate().plusMinutes(exam.getDuration())
                : null;
        boolean isExpired = endTime != null && now.isAfter(endTime);

        return ExamResponse.builder()
                .id(exam.getId())
                .classId(primaryClassId)
                .title(exam.getTitle())
                .examDate(exam.getExamDate())
                .duration(exam.getDuration())
                .status(exam.getStatus())
                .createdAt(exam.getCreatedAt())
                .courseName(primaryCourseName)
                .courseCode(primaryCourseCode)
                .classIds(classIds)
                .classCodes(classCodes)
                .classNames(classNames)
                .assignedClasses(assignedClasses)
                .endTime(endTime)
                .serverTime(now)
                .isExpired(isExpired)
                .problemStatement(exam.getProblemStatement())
                .inputSpecification(exam.getInputSpecification())
                .outputSpecification(exam.getOutputSpecification())
                .constraints(exam.getConstraints())
                .sampleInput(exam.getSampleInput())
                .sampleOutput(exam.getSampleOutput())
                .programmingLanguage(exam.getProgrammingLanguage())
                .sourceDocumentName(exam.getSourceDocumentName())
                .build();
    }
}
