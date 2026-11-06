package com.codeassess.submission.dto.response;

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
public class SubmissionDetailResponse {
    private String id;
    private String examId;
    private String questionId;
    private String questionTitle;
    private String studentId;
    private String status;
    private String sourceCode;
    private BigDecimal totalScore;
    private BigDecimal overrideScore;
    private BigDecimal autoScore;
    private Integer testCasesPassed;
    private Integer testCasesTotal;
    private LocalDateTime evaluatedAt;
    private LocalDateTime createdAt;
    private String submissionType;
    private String originalFilename;
    private String archiveHash;
    private String sourceTreeHash;
    private Integer fileCount;
    private String entryPointClass;
    private String executionStatus;
    private Integer exitCode;
    private String compileOutput;
    private String stdout;
    private String stderr;
    private Long executionDurationMs;
    private String instructorFeedback;
    private String reviewStatus;
    private String publicationStatus;
    private BigDecimal publishedScore;
}
