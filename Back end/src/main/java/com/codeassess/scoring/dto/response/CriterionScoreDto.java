package com.codeassess.scoring.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class CriterionScoreDto {
    private String criterionId;
    private String name;
    private String description;
    private BigDecimal score;
    private BigDecimal maxScore;
}
