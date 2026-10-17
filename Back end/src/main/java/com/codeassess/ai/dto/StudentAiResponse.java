package com.codeassess.ai.dto;

public record StudentAiResponse(
        StudentAiMode mode,
        String explanation,
        String advisoryNotice
) {
    public static final String DEFAULT_ADVISORY =
            "Advisory Notice: AI-generated learning guidance is educational only and may be incomplete or inaccurate. It does not replace teacher assessment, alter test suite outcomes, or modify assignment scores.";

    public StudentAiResponse(StudentAiMode mode, String explanation) {
        this(mode, explanation, DEFAULT_ADVISORY);
    }
}
