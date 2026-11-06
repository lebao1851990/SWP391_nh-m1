package com.codeassess.testcase.dto.sandbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseBatchSuiteResult {
    private boolean compiled;
    private String compileOutput;
    private List<TestCaseExecutionItemResult> itemResults;
    private long totalDurationMs;

    @Builder.Default
    private boolean fencedOut = false;

    public TestCaseBatchSuiteResult(boolean compiled, String compileOutput, List<TestCaseExecutionItemResult> itemResults, long totalDurationMs) {
        this.compiled = compiled;
        this.compileOutput = compileOutput;
        this.itemResults = itemResults;
        this.totalDurationMs = totalDurationMs;
        this.fencedOut = false;
    }
}
