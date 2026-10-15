package com.codeassess.sandbox.exception;

/**
 * Signals an infrastructure-level failure in the sandbox:
 * Docker engine unavailable, container start failed, I/O error, etc.
 *
 * <p>This is intentionally separate from compilation and runtime errors,
 * which are expressed via {@link com.codeassess.sandbox.dto.response.SandboxResult.Status}.
 * {@code SandboxException} is only thrown when the sandbox itself cannot operate,
 * not when student code compiles or runs incorrectly.
 */
public class SandboxException extends RuntimeException {

    public SandboxException(String message) {
        super(message);
    }

    public SandboxException(String message, Throwable cause) {
        super(message, cause);
    }
}
