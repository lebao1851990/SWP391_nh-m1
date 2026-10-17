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
    private List<CriterionScoreDto> criteria;
}
