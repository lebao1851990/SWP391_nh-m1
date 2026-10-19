package com.codeassess.scoring.dto.request;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class SaveScoringPayload {
    private List<CriteriaScoreRequest> criteriaScores;
    
    @DecimalMin(value = "0.0", message = "Override score cannot be negative")
    private BigDecimal overrideTotalScore;

    private String instructorEvaluationComments;
    private String overrideRationale;

    @Data
    public static class CriteriaScoreRequest {
        private String criterionId;
        private BigDecimal score;
    }
}
