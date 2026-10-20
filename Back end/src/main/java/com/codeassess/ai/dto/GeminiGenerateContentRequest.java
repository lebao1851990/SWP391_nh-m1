package com.codeassess.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeminiGenerateContentRequest(
        SystemInstruction system_instruction,
        List<Content> contents,
        GenerationConfig generationConfig
) {
    public record SystemInstruction(List<Part> parts) {}

    public record Content(String role, List<Part> parts) {}

    public record Part(String text) {}

    public record GenerationConfig(Double temperature, Integer maxOutputTokens) {}
}
