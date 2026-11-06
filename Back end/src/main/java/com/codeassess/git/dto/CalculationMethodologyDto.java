package com.codeassess.git.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalculationMethodologyDto {
    private String ethicalNotice;
    private String mergeCommitRule;
    private String revertCommitRule;
    private String lineCountRule;
    private String identityResolutionRule;
    private List<String> limitations;
    private List<String> recommendedInstructorGuidelines;
}

