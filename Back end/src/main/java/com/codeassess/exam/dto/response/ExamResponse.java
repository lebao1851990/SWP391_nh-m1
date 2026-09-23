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
}
