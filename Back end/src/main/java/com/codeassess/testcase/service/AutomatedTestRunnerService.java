package com.codeassess.testcase.service;

import com.codeassess.sandbox.dto.response.SandboxResult;
import com.codeassess.sandbox.service.DockerSandboxService;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.submission.service.SubmissionPersistenceService;
import com.codeassess.testcase.dto.sandbox.TestCaseBatchItem;
import com.codeassess.testcase.dto.sandbox.TestCaseBatchSuiteResult;
import com.codeassess.testcase.dto.sandbox.TestCaseExecutionItemResult;
import com.codeassess.testcase.entity.SubmissionTestResult;
import com.codeassess.testcase.entity.TestCase;
import com.codeassess.testcase.repository.SubmissionTestResultRepository;
import com.codeassess.testcase.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Coordinates automated compilation, test case execution in the Docker Sandbox,
 * and calculation of automatic scores.
 */
@Service
@Slf4j
public class AutomatedTestRunnerService {

    private final DockerSandboxService dockerSandboxService;
    private final TestCaseRepository testCaseRepository;
    private final SubmissionTestResultRepository submissionTestResultRepository;
    private final SubmissionRepository submissionRepository;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.codeassess.submission.service.SubmissionStorageService submissionStorageService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.codeassess.submission.service.ZipExtractionService zipExtractionService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.codeassess.submission.service.EntryPointDiscoveryService entryPointDiscoveryService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.codeassess.plagiarism.service.AstPlagiarismService astPlagiarismService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.codeassess.plagiarism.service.SimilarityMatrixService similarityMatrixService;

    public AutomatedTestRunnerService(
            DockerSandboxService dockerSandboxService,
            TestCaseRepository testCaseRepository,
            SubmissionTestResultRepository submissionTestResultRepository,
            SubmissionRepository submissionRepository) {
        this.dockerSandboxService = dockerSandboxService;
        this.testCaseRepository = testCaseRepository;
        this.submissionTestResultRepository = submissionTestResultRepository;
        this.submissionRepository = submissionRepository;
        this.transactionTemplate = null;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AutomatedTestRunnerService(
            DockerSandboxService dockerSandboxService,
            TestCaseRepository testCaseRepository,
            SubmissionTestResultRepository submissionTestResultRepository,
            SubmissionRepository submissionRepository,
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.dockerSandboxService = dockerSandboxService;
        this.testCaseRepository = testCaseRepository;
        this.submissionTestResultRepository = submissionTestResultRepository;
        this.submissionRepository = submissionRepository;
        this.transactionTemplate = transactionManager != null ? new org.springframework.transaction.support.TransactionTemplate(transactionManager) : null;
    }

    public TestCaseBatchSuiteResult runAndGrade(Submission submission) {
        if (submission == null) {
            throw new IllegalArgumentException("Submission must not be null");
        }

        String examId = submission.getExam() != null ? submission.getExam().getId() : null;
        if (examId == null) {
            throw new IllegalStateException("Submission is not linked to any exam");
        }

        List<TestCase> testCases = testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc(examId);
        boolean isZip = "ZIP".equalsIgnoreCase(submission.getSubmissionType());

        // Case 1: No test cases configured for this exam
        if (testCases.isEmpty()) {
            log.info("Exam {} has no test cases configured; performing compile-and-run check without auto-score", examId);
            SandboxResult sandboxResult = null;

            if (isZip && submissionStorageService != null && submissionStorageService.hasZip(submission.getId()) && zipExtractionService != null) {
                byte[] zipBytes = submissionStorageService.getZipBytes(submission.getId());
                try (com.codeassess.submission.dto.ExtractedWorkspace workspace = zipExtractionService.extract(zipBytes)) {
                    String epClass = (entryPointDiscoveryService != null)
                            ? entryPointDiscoveryService.discoverEntryPoint(workspace).entryPointClass()
                            : submission.getEntryPointClass();
                    try {
                        sandboxResult = dockerSandboxService.executeWorkspace(workspace, epClass);
                    } catch (Exception e) {
                        log.error("Sandbox workspace execution error for submission {}: {}", submission.getId(), e.getMessage(), e);
                        sandboxResult = SandboxResult.builder()
                                .status(SandboxResult.Status.INTERNAL_ERROR)
                                .stderr("Sandbox execution failure: " + e.getMessage())
                                .executionTimeMs(0L)
                                .build();
                    }

                    if (astPlagiarismService != null) {
                        try {
                            astPlagiarismService.generateFingerprintsForWorkspace(submission, workspace);
                        } catch (Exception e) {
                            log.error("Failed to generate AST fingerprints for workspace {}: {}", submission.getId(), e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to extract workspace for submission {}: {}", submission.getId(), e.getMessage(), e);
                    sandboxResult = SandboxResult.builder()
                            .status(SandboxResult.Status.INTERNAL_ERROR)
                            .stderr("Workspace extraction failure: " + e.getMessage())
                            .executionTimeMs(0L)
                            .build();
                }
            } else {
                try {
                    sandboxResult = dockerSandboxService.execute(submission.getSourceCode());
                } catch (Exception e) {
                    log.error("Sandbox execution error for submission {}: {}", submission.getId(), e.getMessage(), e);
                    sandboxResult = SandboxResult.builder()
                            .status(SandboxResult.Status.INTERNAL_ERROR)
                            .stderr("Sandbox execution failure: " + e.getMessage())
                            .executionTimeMs(0L)
                            .build();
                }
            }

            if (sandboxResult == null) {
                sandboxResult = SandboxResult.builder()
                        .status(SandboxResult.Status.INTERNAL_ERROR)
                        .stderr("Sandbox execution produced null result")
                        .executionTimeMs(0L)
                        .build();
            }

            String execStatus = sandboxResult.getStatus() != null ? sandboxResult.getStatus().name() : "INTERNAL_ERROR";
            submission.setExecutionStatus(execStatus);
            submission.setExitCode(sandboxResult.getExitCode());
            submission.setCompileOutput(SubmissionPersistenceService.safeTruncateUtf8(
                    sandboxResult.getCompilerOutput(), SubmissionPersistenceService.MAX_OUTPUT_BYTES));
            submission.setStdout(SubmissionPersistenceService.safeTruncateUtf8(
                    sandboxResult.getStdout(), SubmissionPersistenceService.MAX_OUTPUT_BYTES));
            submission.setStderr(SubmissionPersistenceService.safeTruncateUtf8(
                    sandboxResult.getStderr(), SubmissionPersistenceService.MAX_OUTPUT_BYTES));
            submission.setExecutionDurationMs(sandboxResult.getExecutionTimeMs());

            // Section F2: If no test cases, do not assign 0 or 100 secretly
            submission.setAutoScore(null);
            submission.setTestCasesPassed(0);
            submission.setTestCasesTotal(0);
            submission.setStatus("COMPLETED");
            submission.setEvaluatedAt(LocalDateTime.now());
            submission.setClaimToken(null);
            submission.setLeaseExpiresAt(null);
            if (transactionTemplate != null) {
                transactionTemplate.executeWithoutResult(status -> submissionRepository.save(submission));
            } else {
                submissionRepository.save(submission);
            }

            if (similarityMatrixService != null) {
                try {
                    similarityMatrixService.requestRefresh(examId);
                    similarityMatrixService.generateMatrixForExam(examId);
                } catch (Exception e) {
                    log.warn("Matrix refresh error for exam {}: {}", examId, e.getMessage());
                }
            }

            return TestCaseBatchSuiteResult.builder()
                    .compiled("SUCCESS".equals(execStatus))
                    .compileOutput(sandboxResult.getCompilerOutput())
                    .itemResults(new ArrayList<>())
                    .totalDurationMs(sandboxResult.getExecutionTimeMs())
                    .build();
        }

        // Case 2: Test cases are configured — run batch execution
        List<TestCaseBatchItem> batchItems = testCases.stream().map(tc -> TestCaseBatchItem.builder()
                .id(tc.getId())
                .name(tc.getName())
                .inputData(tc.getInputData())
                .expectedOutput(tc.getExpectedOutput())
                .points(tc.getPoints())
                .isHidden(tc.getIsHidden())
                .timeoutSeconds(5)
                .build()).collect(Collectors.toList());

        TestCaseBatchSuiteResult suiteResult;

        if (isZip && submissionStorageService != null && submissionStorageService.hasZip(submission.getId()) && zipExtractionService != null) {
            byte[] zipBytes = submissionStorageService.getZipBytes(submission.getId());
            try (com.codeassess.submission.dto.ExtractedWorkspace workspace = zipExtractionService.extract(zipBytes)) {
                String epClass = (entryPointDiscoveryService != null)
                        ? entryPointDiscoveryService.discoverEntryPoint(workspace).entryPointClass()
                        : submission.getEntryPointClass();

                try {
                    suiteResult = dockerSandboxService.executeTestCaseSuiteForWorkspace(workspace, epClass, batchItems);
                } catch (Exception e) {
                    log.error("Sandbox workspace test suite execution failure for submission {}: {}", submission.getId(), e.getMessage(), e);
                    List<TestCaseExecutionItemResult> errorItems = testCases.stream().map(tc -> TestCaseExecutionItemResult.builder()
                            .testCaseId(tc.getId())
                            .testCaseName(tc.getName())
                            .isHidden(tc.getIsHidden())
                            .status("INTERNAL_ERROR")
                            .errorMessage("Sandbox failure: " + e.getMessage())
                            .pointsAwarded(BigDecimal.ZERO)
                            .maxPoints(tc.getPoints())
                            .executionTimeMs(0L)
                            .build()).collect(Collectors.toList());

                    suiteResult = TestCaseBatchSuiteResult.builder()
                            .compiled(false)
                            .compileOutput("Sandbox failure: " + e.getMessage())
                            .itemResults(errorItems)
                            .totalDurationMs(0L)
                            .build();
                }

                if (astPlagiarismService != null) {
                    try {
                        astPlagiarismService.generateFingerprintsForWorkspace(submission, workspace);
                    } catch (Exception e) {
                        log.error("Failed to generate AST fingerprints for workspace {}: {}", submission.getId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to extract workspace for submission {}: {}", submission.getId(), e.getMessage(), e);
                List<TestCaseExecutionItemResult> errorItems = testCases.stream().map(tc -> TestCaseExecutionItemResult.builder()
                        .testCaseId(tc.getId())
                        .testCaseName(tc.getName())
                        .isHidden(tc.getIsHidden())
                        .status("INTERNAL_ERROR")
                        .errorMessage("Workspace extraction failure: " + e.getMessage())
                        .pointsAwarded(BigDecimal.ZERO)
                        .maxPoints(tc.getPoints())
                        .executionTimeMs(0L)
                        .build()).collect(Collectors.toList());

                suiteResult = TestCaseBatchSuiteResult.builder()
                        .compiled(false)
                        .compileOutput("Workspace extraction failure: " + e.getMessage())
                        .itemResults(errorItems)
                        .totalDurationMs(0L)
                        .build();
            }
        } else {
            try {
                suiteResult = dockerSandboxService.executeTestCaseSuite(submission.getSourceCode(), batchItems);
            } catch (Exception e) {
                log.error("Sandbox test suite execution failure for submission {}: {}", submission.getId(), e.getMessage(), e);
                List<TestCaseExecutionItemResult> errorItems = testCases.stream().map(tc -> TestCaseExecutionItemResult.builder()
                        .testCaseId(tc.getId())
                        .testCaseName(tc.getName())
                        .isHidden(tc.getIsHidden())
                        .status("INTERNAL_ERROR")
                        .errorMessage("Sandbox failure: " + e.getMessage())
                        .pointsAwarded(BigDecimal.ZERO)
                        .maxPoints(tc.getPoints())
                        .executionTimeMs(0L)
                        .build()).collect(Collectors.toList());

                suiteResult = TestCaseBatchSuiteResult.builder()
                        .compiled(false)
                        .compileOutput("Sandbox failure: " + e.getMessage())
                        .itemResults(errorItems)
                        .totalDurationMs(0L)
                        .build();
            }
        }

        Map<String, TestCase> testCaseMap = testCases.stream()
                .collect(Collectors.toMap(TestCase::getId, Function.identity(), (a, b) -> a));

        List<SubmissionTestResult> resultsToSave = new ArrayList<>();
        int passedTests = 0;
        BigDecimal passedWeight = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;

        for (TestCaseExecutionItemResult item : suiteResult.getItemResults()) {
            TestCase tc = testCaseMap.get(item.getTestCaseId());
            BigDecimal maxPts = (tc != null && tc.getPoints() != null) ? tc.getPoints() : BigDecimal.valueOf(10.00);
            totalWeight = totalWeight.add(maxPts);

            boolean isPassed = "PASSED".equalsIgnoreCase(item.getStatus());
            if (isPassed) {
                passedTests++;
                passedWeight = passedWeight.add(item.getPointsAwarded() != null ? item.getPointsAwarded() : maxPts);
            }

            SubmissionTestResult result = SubmissionTestResult.builder()
                    .submission(submission)
                    .testCase(tc)
                    .testCaseName(item.getTestCaseName())
                    .isHidden(item.getIsHidden() != null ? item.getIsHidden() : false)
                    .status(item.getStatus() != null ? item.getStatus() : "FAILED")
                    .actualOutput(SubmissionPersistenceService.safeTruncateUtf8(
                            item.getActualOutput(), SubmissionPersistenceService.MAX_OUTPUT_BYTES))
                    .expectedOutput(SubmissionPersistenceService.safeTruncateUtf8(
                            item.getExpectedOutput(), SubmissionPersistenceService.MAX_OUTPUT_BYTES))
                    .executionTimeMs(item.getExecutionTimeMs())
                    .exitCode(item.getExitCode())
                    .errorMessage(SubmissionPersistenceService.safeTruncateUtf8(
                            item.getErrorMessage(), SubmissionPersistenceService.MAX_OUTPUT_BYTES))
                    .pointsAwarded(item.getPointsAwarded() != null ? item.getPointsAwarded() : BigDecimal.ZERO)
                    .build();

            resultsToSave.add(result);
        }

        // Section F1: autoScore = maxScore * passedWeight / totalWeight
        int totalTests = testCases.size();
        BigDecimal autoScore;
        if (totalWeight.compareTo(BigDecimal.ZERO) > 0) {
            autoScore = BigDecimal.valueOf(100.0)
                    .multiply(passedWeight)
                    .divide(totalWeight, 2, RoundingMode.HALF_UP);
        } else if (totalTests > 0) {
            autoScore = BigDecimal.valueOf(100.0)
                    .multiply(BigDecimal.valueOf(passedTests))
                    .divide(BigDecimal.valueOf(totalTests), 2, RoundingMode.HALF_UP);
        } else {
            autoScore = BigDecimal.ZERO;
        }

        // Clamp autoScore between 0.00 and 100.00
        if (autoScore.compareTo(BigDecimal.ZERO) < 0) {
            autoScore = BigDecimal.ZERO;
        } else if (autoScore.compareTo(BigDecimal.valueOf(100.0)) > 0) {
            autoScore = BigDecimal.valueOf(100.0);
        }

        boolean isInfrastructureFailure = !resultsToSave.isEmpty() && resultsToSave.stream()
                .allMatch(r -> "INTERNAL_ERROR".equalsIgnoreCase(r.getStatus()));

        if (isInfrastructureFailure) {
            // Section F1 & F2: Infrastructure failure must NOT be graded as student failure (0.00 score)
            submission.setAutoScore(null);
            submission.setTestCasesPassed(0);
            submission.setTestCasesTotal(totalTests);
            submission.setExecutionStatus("INTERNAL_ERROR");
            submission.setExitCode(null);
            submission.setCompileOutput(suiteResult.getCompileOutput());
            submission.setStatus("FAILED"); // System/pipeline failure, allowing audit or re-grading
        } else {
            submission.setAutoScore(autoScore);
            submission.setTestCasesPassed(passedTests);
            submission.setTestCasesTotal(totalTests);

            // If teacher has not manually graded total_score, set total_score to auto_score
            if (submission.getTotalScore() == null) {
                submission.setTotalScore(autoScore);
            }

            if (!suiteResult.isCompiled()) {
                submission.setExecutionStatus("COMPILE_ERROR");
                submission.setExitCode(1);
                submission.setCompileOutput(suiteResult.getCompileOutput());
            } else if (passedTests == totalTests) {
                submission.setExecutionStatus("SUCCESS");
                submission.setExitCode(0);
            } else {
                submission.setExecutionStatus(passedTests > 0 ? "PARTIAL_SUCCESS" : "FAILED");
                submission.setExitCode(2);
            }
            submission.setStatus("COMPLETED");
        }

        submission.setEvaluatedAt(LocalDateTime.now());
        submission.setExecutionDurationMs(suiteResult.getTotalDurationMs());
        submission.setClaimToken(null);
        submission.setLeaseExpiresAt(null);

        // Atomically delete old test results, save new results, and update submission
        final List<SubmissionTestResult> finalResultsToSave = resultsToSave;
        Runnable persistAction = () -> {
            submissionTestResultRepository.deleteBySubmission_Id(submission.getId());
            submissionTestResultRepository.saveAll(finalResultsToSave);
            submissionRepository.save(submission);
        };

        if (transactionTemplate != null) {
            transactionTemplate.executeWithoutResult(status -> persistAction.run());
        } else {
            persistAction.run();
        }

        log.info("Graded submission {} with {} test cases: {} passed, auto_score = {}",
                submission.getId(), totalTests, passedTests, autoScore);

        return suiteResult;
    }
}
