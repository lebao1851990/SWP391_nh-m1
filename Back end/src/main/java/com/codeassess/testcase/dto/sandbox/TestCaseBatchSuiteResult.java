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
}
