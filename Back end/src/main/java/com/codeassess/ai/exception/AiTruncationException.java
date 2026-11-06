package com.codeassess.ai.exception;

/**
 * Thrown when an AI provider's response exceeds the allocated output token budget
 * or is cut off prematurely (e.g. finishReason = MAX_TOKENS or truncated JSON).
 */
public class AiTruncationException extends AiIntegrationException {

    public AiTruncationException(String message) {
        super(message);
    }

    public AiTruncationException(String message, Throwable cause) {
        super(message, cause);
    }
}

