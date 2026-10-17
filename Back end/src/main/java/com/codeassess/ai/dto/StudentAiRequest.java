package com.codeassess.ai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StudentAiRequest(
        @NotNull(message = "Mode is required")
        StudentAiMode mode,

        @Size(max = 8192, message = "Compiler output cannot exceed 8192 characters")
        String compilerOutput
) {
}
