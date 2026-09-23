package com.codeassess.ai.dto;

import jakarta.validation.constraints.NotNull;

public record AiCodeReviewRequest(
    @NotNull(message = "evidenceId is required")
    String evidenceId
) {
}
