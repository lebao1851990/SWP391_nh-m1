package com.codeassess.scoring.dto.request;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class SaveScoringPayload {
    private List<CriteriaScoreRequest> criteriaScores;
    private BigDecimal overrideTotalScore;

    @Data
    public static class CriteriaScoreRequest {
        private String criterionId;
        private BigDecimal score;
    }
}
