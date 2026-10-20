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
public class TestCaseExecutionItemResult {
    private String testCaseId;
    private String testCaseName;
    private Boolean isHidden;
    private String status; // PASSED, FAILED, COMPILE_ERROR, TIMEOUT, RUNTIME_ERROR
    private String actualOutput;
    private String expectedOutput;
    private Long executionTimeMs;
    private Integer exitCode;
    private String errorMessage;
    private BigDecimal pointsAwarded;
    private BigDecimal maxPoints;
}
