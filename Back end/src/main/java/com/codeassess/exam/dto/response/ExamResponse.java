package com.codeassess.exam.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ExamResponse {
    private String id;
    private String classId;
    private String title;
    private LocalDateTime examDate;
    private Integer duration;
    private String status;
    private LocalDateTime createdAt;
    
    // Additional fields for detail view
    private String courseName;
    private String courseCode;
    private Integer enrolledCount;
    private Integer submittedCount;

    // Multi-class support
    private java.util.List<String> classIds;
    private java.util.List<String> classCodes;
    private java.util.List<String> classNames;
    private java.util.List<ClassSummaryDto> assignedClasses;
}
