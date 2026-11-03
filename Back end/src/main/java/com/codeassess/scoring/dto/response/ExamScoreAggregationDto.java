package com.codeassess.scoring.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamScoreAggregationDto {
    private String examId;
    private String examTitle;
    private String studentId;
    private String studentName;
    private BigDecimal examScore10;
    private BigDecimal totalPointsEarned;
    private BigDecimal totalMaxPoints;
    private String status;
    @JsonProperty("isProvisional")
    private boolean isProvisional;
    private int totalQuestions;
    private int submittedQuestions;
    private int completedQuestions;
    private int pendingQuestions;
    private List<QuestionScoreDetailDto> questionScores;
}

