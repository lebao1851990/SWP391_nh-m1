package com.codeassess.queue.exception;

/**
 * Thrown when an error occurs while publishing a job to the distributed queue.
 */
public class QueuePublicationException extends RuntimeException {

    public QueuePublicationException(String message) {
        super(message);
    }

    public QueuePublicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
