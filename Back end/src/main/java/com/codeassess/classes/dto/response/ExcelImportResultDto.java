package com.codeassess.classes.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelImportResultDto {
    private int totalRows;
    private int importedCount;
    private int failedCount;
    private boolean isStrict;

    @Builder.Default
    private List<ExcelRowErrorDto> errors = new ArrayList<>();
}
