package com.codeassess.common.exception;

import com.codeassess.classes.dto.response.ExcelImportResultDto;
import lombok.Getter;

@Getter
public class ExcelBatchImportException extends RuntimeException {

    private final ExcelImportResultDto result;

    public ExcelBatchImportException(String message, ExcelImportResultDto result) {
        super(message);
        this.result = result;
    }
}
