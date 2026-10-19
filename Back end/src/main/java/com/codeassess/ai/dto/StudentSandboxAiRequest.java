package com.codeassess.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StudentSandboxAiRequest(
        @NotNull(message = "Mode is required")
        StudentAiMode mode,

        @NotBlank(message = "Source code is required")
        @Size(max = 65536, message = "Source code cannot exceed 65536 characters")
        String sourceCode,

        @Size(max = 8192, message = "Compiler output cannot exceed 8192 characters")
        String compilerOutput
) {
}
