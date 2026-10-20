package com.codeassess.submission.exception;

/**
 * Thrown when an uploaded ZIP archive violates safety, integrity, or structure constraints.
 */
public class ZipValidationException extends RuntimeException {

    public enum ErrorCode {
        EMPTY_ARCHIVE,
        NOT_A_ZIP_ARCHIVE,
        EXCEEDS_MAX_COMPRESSED_SIZE,
        EXCEEDS_MAX_EXTRACTED_SIZE,
        EXCEEDS_MAX_FILE_COUNT,
        EXCEEDS_MAX_FILE_SIZE,
        EXCEEDS_COMPRESSION_RATIO,
        ZIP_SLIP_ATTEMPT,
        ABSOLUTE_PATH_DISALLOWED,
        DUPLICATE_OR_COLLIDING_PATH,
        SYMLINK_DISALLOWED,
        FORBIDDEN_FILE_TYPE,
        NO_JAVA_SOURCE_FOUND,
        EXTRACTION_FAILED
    }

    private final ErrorCode errorCode;

    public ZipValidationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ZipValidationException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
