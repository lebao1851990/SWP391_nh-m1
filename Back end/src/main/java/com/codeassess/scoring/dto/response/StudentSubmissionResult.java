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
    private LocalDateTime evaluatedAt;
    
    private BigDecimal totalScore;
    private BigDecimal overrideTotalScore;
    private BigDecimal effectiveScore;
    private BigDecimal maxTotalScore;
    
    private List<CriterionScoreDto> criteria;
}
