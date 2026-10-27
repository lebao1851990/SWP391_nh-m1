package com.codeassess.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentAnalysisResponse {

    private String documentName;
    private long documentSizeBytes;
    private int characterCount;
    private String documentPreview;

    private String mainTopic;

    @Builder.Default
    private List<String> subTopics = new ArrayList<>();

    @Builder.Default
    private List<String> keyConcepts = new ArrayList<>();

    @Builder.Default
    private List<String> extractedSnippets = new ArrayList<>();

    private String suggestedDifficulty;

    @Builder.Default
    private List<String> suggestedProblemTypes = new ArrayList<>();

    private boolean isSimulation;
}
