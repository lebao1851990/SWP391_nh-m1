package com.codeassess.plagiarism.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeComparisonDataDto {
    private String pairId;
    private String fileA;
    private String fileB;
    private String sourceCodeA;
    private String sourceCodeB;
    private List<MatchDto> matches;
    private String adjudicationStatus;
    private String adjudicationNotes;
    private String adjudicatedBy;
    private LocalDateTime adjudicatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchDto {
        private int startLineA;
        private int endLineA;
        private int startLineB;
        private int endLineB;
        private String type;
    }
}
