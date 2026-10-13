package com.codeassess.sandbox.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /api/student/sandbox/execute}.
 *
 * <p>UTF-8 byte-length enforcement (max 65,536 bytes) is applied in
 * {@link com.codeassess.sandbox.service.DockerSandboxService}, not here,
 * because Bean Validation {@code @Size} counts characters, not encoded bytes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxExecuteRequest {

    @NotBlank(message = "sourceCode must not be blank")
    private String sourceCode;
}
