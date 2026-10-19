package com.codeassess.plagiarism.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class AppealResponseDto {
    private String id;
    private String submissionId;
    private String examName;
    private String status;
    private String basis;
    private String explanation;
    private String studentName;
    private String studentEmail;
    private String reviewerNotes;
    private BigDecimal currentScore;
    private BigDecimal originalScore;
    private BigDecimal updatedScore;
    private BigDecimal autoScore;
    private String sourceCode;
    private Integer testCasesPassed;
    private Integer testCasesTotal;
    private String reviewerName;
    private String reviewerEmail;
    private String submissionStatus;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}

