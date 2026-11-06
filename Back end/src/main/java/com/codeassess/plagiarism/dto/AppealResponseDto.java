package com.codeassess.plagiarism.dto;

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
public class AppealResponseDto {
    private String id;
    private String submissionId;
    private String examName;
    private String questionId;
    private String questionTitle;
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
    private BigDecimal publishedScore;
    private String publicationStatus;
    private String reviewStatus;
    private String sourceCode;
    private Integer testCasesPassed;
    private Integer testCasesTotal;
    private String reviewerName;
    private String reviewerEmail;
    private String adminName;
    private String resolutionAction;
    private String submissionStatus;
    private String originalFilename;
    private String submissionType;
    private Long fileSize;
    private Boolean hasArchive;
    private String executionStatus;
    private String compileOutput;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
