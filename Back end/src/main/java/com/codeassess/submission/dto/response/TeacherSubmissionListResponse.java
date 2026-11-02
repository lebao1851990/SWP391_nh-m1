package com.codeassess.submission.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TeacherSubmissionListResponse {
    private String id;
    private String studentName;
    private String studentEmail;
    private String examTitle;
    private String courseCode;
    private String status;
    private BigDecimal totalScore;
    private LocalDateTime submittedAt; // Maps to createdAt or evaluatedAt based on status

    private String originalFilename;
    private String executionStatus;
    private BigDecimal autoScore;
    private Integer testCasesPassed;
    private Integer testCasesTotal;
    private String plagiarismStatus;
    private BigDecimal maxSimilarityRate;
    private String instructorFeedback;
}
