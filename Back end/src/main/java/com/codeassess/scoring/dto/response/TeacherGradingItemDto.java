package com.codeassess.scoring.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherGradingItemDto {
    private String submissionId;
    private String examId;
    private String examTitle;
    private String questionId;
    private String questionTitle;
    private Integer questionOrder;
    private String studentId;
    private String studentName;
    private String studentEmail;
    private LocalDateTime submissionTime;
    private Integer attemptNumber;
    private BigDecimal provisionalScore;
    private BigDecimal maxScore;
    private String reviewStatus;
    private String publicationStatus;
    private String executionStatus;
    private BigDecimal approvedScore;
    private BigDecimal publishedScore;
    private Integer testCasesPassed;
    private Integer testCasesTotal;
}

