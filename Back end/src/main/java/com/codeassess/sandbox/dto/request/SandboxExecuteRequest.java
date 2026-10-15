package com.codeassess.sandbox.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /api/student/sandbox/execute}.
 *
 * <p>Validates that sourceCode is not blank and does not exceed 65,536 characters.
 * UTF-8 byte-length enforcement (max 65,536 bytes) is additionally verified in
 * {@link com.codeassess.sandbox.service.DockerSandboxService}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxExecuteRequest {

    @NotBlank(message = "sourceCode must not be blank")
    @Size(max = 65536, message = "sourceCode exceeds the maximum allowed size of 65536 characters")
    private String sourceCode;
}
