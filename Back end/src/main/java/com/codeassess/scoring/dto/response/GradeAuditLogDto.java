package com.codeassess.scoring.dto.response;

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
public class GradeAuditLogDto {
    private String id;
    private String submissionId;
    private String actorId;
    private String actorName;
    private String actorEmail;
    private String actionType;
    private BigDecimal preRubricScore;
    private BigDecimal postRubricScore;
    private BigDecimal preOverrideScore;
    private BigDecimal postOverrideScore;
    private BigDecimal preEffectiveScore;
    private BigDecimal postEffectiveScore;
    private String rationale;
    private LocalDateTime createdAt;
}
