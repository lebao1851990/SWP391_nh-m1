package com.codeassess.sandbox.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * The result returned to the caller after sandbox execution.
 *
 * <p>{@code status} distinguishes:
 * <ul>
 *   <li>{@link Status#SUCCESS} – compiled and exited 0</li>
 *   <li>{@link Status#COMPILE_ERROR} – javac returned non-zero</li>
 *   <li>{@link Status#RUNTIME_ERROR} – ran but exited non-zero</li>
 *   <li>{@link Status#TIMEOUT} – container forcibly stopped after timeout</li>
 *   <li>{@link Status#INTERNAL_ERROR} – Docker infrastructure failure</li>
 * </ul>
 *
 * <p>stdout and stderr are bounded by {@code sandbox.max-output-bytes}. A
 * {@code truncated} flag is set when either stream was cut. Internal host
 * paths, credentials, or database details are never included here.
 */
@Data
@Builder
public class SandboxResult {

    public enum Status {
        SUCCESS,
        COMPILE_ERROR,
        RUNTIME_ERROR,
        TIMEOUT,
        INTERNAL_ERROR,
        COMPILE_ONLY_SUCCESS,
        AMBIGUOUS_MAIN_METHOD,
        CAPACITY_EXCEEDED
    }

    private Status status;
    private String stdout;
    private String stderr;
    private String compilerOutput;
    private Integer exitCode;
    private long executionTimeMs;

    /** True if stdout or stderr was truncated to the max-output-bytes limit. */
    private boolean truncated;
}
