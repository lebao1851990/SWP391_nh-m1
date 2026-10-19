package com.codeassess.testcase.dto.sandbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseBatchItem {
    private String id;
    private String name;
    private String inputData;
    private String expectedOutput;
    private BigDecimal points;
    private Boolean isHidden;

    @Builder.Default
    private int timeoutSeconds = 5;
}
