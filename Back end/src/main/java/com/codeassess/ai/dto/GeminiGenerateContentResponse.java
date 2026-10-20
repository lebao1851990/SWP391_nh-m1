package com.codeassess.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiGenerateContentResponse(
        List<Candidate> candidates,
        PromptFeedback promptFeedback,
        UsageMetadata usageMetadata
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(
            Content content,
            String finishReason,
            Integer index
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(
            List<Part> parts,
            String role
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Part(
            String text
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PromptFeedback(
            String blockReason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UsageMetadata(
            Integer promptTokenCount,
            Integer candidatesTokenCount,
            Integer totalTokenCount
    ) {}
}
