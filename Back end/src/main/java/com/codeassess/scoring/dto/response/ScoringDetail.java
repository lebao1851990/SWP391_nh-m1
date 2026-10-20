package com.codeassess.scoring.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ScoringDetail {
    private String submissionId;
    private String studentId;
    private String studentName;
    private String examName;
    private String className;
    private LocalDateTime evaluatedAt;
    private String status;
    private BigDecimal totalScore;
    private BigDecimal overrideTotalScore;
    private BigDecimal effectiveScore;
    private BigDecimal maxTotalScore;
    private BigDecimal autoScore;
    private Integer testCasesPassed;
    private Integer testCasesTotal;
    private List<com.codeassess.testcase.dto.response.SubmissionTestResultDto> testResults;
    private List<CriterionScoreDto> criteria;
    private String instructorFeedback;
    private String originalFilename;
    private String submissionType;
    private String archiveHash;
    private String executionStatus;
    private String compileOutput;
    private String stdout;
    private String stderr;
}
