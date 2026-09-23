package com.codeassess.plagiarism.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimilarityPairDto {
    private String pairId;
    private String submissionAId;
    private String submissionBId;
    private String studentAId;
    private String studentBId;
    private String studentAName;
    private String studentBName;
    private BigDecimal similarityRate;
    private boolean isFlagged;
}
