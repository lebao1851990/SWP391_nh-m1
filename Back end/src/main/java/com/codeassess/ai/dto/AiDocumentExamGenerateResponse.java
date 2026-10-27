package com.codeassess.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDocumentExamGenerateResponse {

    private String title;
    private String problemStatement;
    private String inputSpecification;
    private String outputSpecification;
    private String constraints;
    private String sampleInput;
    private String sampleOutput;

    private String programmingLanguage;
    private String difficulty;
    private String sourceDocumentName;

    @Builder.Default
    private List<String> learningObjectives = new ArrayList<>();

    @Builder.Default
    private List<String> sourceReferences = new ArrayList<>();

    private String documentTopicSummary;

    @Builder.Default
    private List<AiTestCaseDraftDto> testCases = new ArrayList<>();

    @Builder.Default
    private List<AiRubricItemDto> rubricCriteria = new ArrayList<>();

    private BigDecimal totalRubricScore;

    private boolean isSimulation;
}
