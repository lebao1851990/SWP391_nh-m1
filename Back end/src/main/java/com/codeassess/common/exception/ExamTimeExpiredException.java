package com.codeassess.common.exception;

public class ExamTimeExpiredException extends RuntimeException {
    public ExamTimeExpiredException(String message) {
        super(message);
    }
}
