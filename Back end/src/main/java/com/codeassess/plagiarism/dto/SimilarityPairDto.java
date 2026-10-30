package com.codeassess.plagiarism.dto;

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
public class SimilarityPairDto {
    private String pairId;
    private String submissionAId;
    private String submissionBId;
    private String studentAId;
    private String studentBId;
    private String studentAName;
    private String studentBName;
    private BigDecimal similarityRate;
    @com.fasterxml.jackson.annotation.JsonProperty("isFlagged")
    @com.fasterxml.jackson.annotation.JsonAlias({"flagged", "isFlagged"})
    private boolean isFlagged;

    @com.fasterxml.jackson.annotation.JsonProperty("isFlagged")
    public boolean isFlagged() {
        return this.isFlagged;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("isFlagged")
    public void setFlagged(boolean flagged) {
        this.isFlagged = flagged;
    }

    @com.fasterxml.jackson.annotation.JsonProperty(value = "flagged", access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public boolean getFlagged() {
        return this.isFlagged;
    }
    private String examId;
    private String examTitle;
    private String className;
    private String adjudicationStatus;
    private String adjudicationNotes;
    private String adjudicatedBy;
    private LocalDateTime adjudicatedAt;
}
