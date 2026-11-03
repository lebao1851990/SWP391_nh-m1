package com.codeassess.scoring.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class QuestionScoreDetailDto {
    private String questionId;
    private String questionTitle;
    private Integer displayOrder;
    private BigDecimal maxScore;
    private BigDecimal autoScore;
    private BigDecimal overrideScore;
    private BigDecimal effectiveScore;
    private String overrideReason;
    private String overriddenBy;
    private LocalDateTime overriddenAt;
    private String status;
    @JsonProperty("isOverridden")
    private boolean isOverridden;
    private String latestSubmissionId;
    private Integer submissionCount;
    private LocalDateTime lastSubmittedAt;
}

