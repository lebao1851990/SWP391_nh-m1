package com.codeassess.classes.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelRowErrorDto {
    private int rowNumber;
    private String identifier;
    private String errorCode;
    private String message;
}
