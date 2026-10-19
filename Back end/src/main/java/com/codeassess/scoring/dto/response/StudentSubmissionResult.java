package com.codeassess.scoring.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class StudentSubmissionResult {
    private String submissionId;
    private String examName;
    private String status;
    private String executionStatus;
    private String compileOutput;
    private LocalDateTime evaluatedAt;
    
    private BigDecimal totalScore;
    private BigDecimal overrideTotalScore;
    private BigDecimal effectiveScore;
    private BigDecimal maxTotalScore;
    
    private BigDecimal autoScore;
    private Integer testCasesPassed;
    private Integer testCasesTotal;
    private List<com.codeassess.testcase.dto.response.SubmissionTestResultDto> testResults;
    
    private List<CriterionScoreDto> criteria;
}
