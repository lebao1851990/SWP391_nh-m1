package com.codeassess.testcase.service;

import com.codeassess.sandbox.dto.response.SandboxResult;
import com.codeassess.sandbox.exception.SandboxException;
import com.codeassess.sandbox.service.DockerSandboxService;
import com.codeassess.scoring.entity.GradeAuditLog;
import com.codeassess.scoring.repository.GradeAuditLogRepository;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Coordinates automated compilation, test case execution in the Docker Sandbox,
 * and fenced transactional persistence of automatic scores.
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

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private GradeAuditLogRepository gradeAuditLogRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.codeassess.scoring.repository.StudentQuestionScoreRepository studentQuestionScoreRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.codeassess.exam.repository.ExamQuestionRepository examQuestionRepository;

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
        this.transactionTemplate = transactionManager != null
                ? new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                : null;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AutomatedTestRunnerService(
            DockerSandboxService dockerSandboxService,
            TestCaseRepository testCaseRepository,
            SubmissionTestResultRepository submissionTestResultRepository,
            SubmissionRepository submissionRepository,
            org.springframework.transaction.PlatformTransactionManager transactionManager,
            com.codeassess.scoring.repository.StudentQuestionScoreRepository studentQuestionScoreRepository) {
        this.dockerSandboxService = dockerSandboxService;
        this.testCaseRepository = testCaseRepository;
        this.submissionTestResultRepository = submissionTestResultRepository;
        this.submissionRepository = submissionRepository;
        this.transactionTemplate = transactionManager != null
                ? new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                : null;
        this.studentQuestionScoreRepository = studentQuestionScoreRepository;
    }

    public TestCaseBatchSuiteResult runAndGrade(Submission submission) {
        return runAndGrade(submission, null);
    }

    /**
     * Executes sandbox grading outside the DB transaction and persists the outcome
     * inside a short, fenced transaction verified by {@code claimToken} (when provided).
     *
     * @param submission the submission to grade
     * @param claimToken optional worker fencing token; if non-null, verified before any DB write
     * @return suite execution result (with {@code fencedOut=true} if the worker lost its lease)
     * @throws SandboxException if an infrastructure or capacity failure occurs in the sandbox
     */
    public TestCaseBatchSuiteResult runAndGrade(Submission submission, String claimToken) {
        if (submission == null) {
            throw new IllegalArgumentException("Submission must not be null");
        }

        String examId = submission.getExam() != null ? submission.getExam().getId() : null;
        if (examId == null) {
            throw new IllegalStateException("Submission is not linked to any exam");
        }

        List<TestCase> testCases;
        if (submission.getQuestion() != null && submission.getQuestion().getId() != null) {
            testCases = testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc(submission.getQuestion().getId());
        } else {
            testCases = testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc(examId);
        }
        if (testCases == null) {
            testCases = java.util.Collections.emptyList();
        }

        boolean isZip = "ZIP".equalsIgnoreCase(submission.getSubmissionType());

        // Case 1: No test cases configured for this question or exam
        if (testCases.isEmpty()) {
            if (submission.getQuestion() != null && submission.getQuestion().getId() != null) {
                log.info("Question {} has no test cases configured; performing compile-and-run check without auto-score",
                        submission.getQuestion().getId());
            } else {
                log.info("Exam {} has no test cases configured; performing compile-and-run check without auto-score", examId);
            }

            if (isZip && submissionStorageService != null && submissionStorageService.hasZip(submission.getId()) && zipExtractionService != null) {
                byte[] zipBytes = submissionStorageService.getZipBytes(submission.getId());
                try (com.codeassess.submission.dto.ExtractedWorkspace workspace = extractWorkspaceOrThrow(submission.getId(), zipBytes)) {
                    String epClass = (entryPointDiscoveryService != null)
                            ? entryPointDiscoveryService.discoverEntryPoint(workspace).entryPointClass()
                            : submission.getEntryPointClass();

                    SandboxResult sandboxResult = executeWorkspaceOrThrow(submission.getId(), workspace, epClass);
                    validateSandboxResultNotInfrastructureFailure(submission.getId(), sandboxResult);

                    boolean persisted = persistNoTestCasesOutcome(submission, sandboxResult, claimToken, true);
                    if (!persisted) {
                        return TestCaseBatchSuiteResult.builder()
                                .compiled(false)
                                .itemResults(new ArrayList<>())
                                .fencedOut(true)
                                .build();
                    }

                    if (astPlagiarismService != null) {
                        try {
                            astPlagiarismService.generateFingerprintsForWorkspace(submission, workspace);
                        } catch (Exception e) {
                            log.error("Failed to generate AST fingerprints for workspace {}: {}", submission.getId(), e.getMessage());
                        }
                    }

                    triggerStandaloneMatrixRefreshIfNeeded(examId, claimToken);

                    return TestCaseBatchSuiteResult.builder()
                            .compiled("SUCCESS".equals(submission.getExecutionStatus()))
                            .compileOutput(sandboxResult.getCompilerOutput())
                            .itemResults(new ArrayList<>())
                            .totalDurationMs(sandboxResult.getExecutionTimeMs())
                            .fencedOut(false)
                            .build();
                }
            } else {
                SandboxResult sandboxResult = executeSingleFileOrThrow(submission);
                validateSandboxResultNotInfrastructureFailure(submission.getId(), sandboxResult);

                boolean persisted = persistNoTestCasesOutcome(submission, sandboxResult, claimToken, isZip);
                if (!persisted) {
                    return TestCaseBatchSuiteResult.builder()
                            .compiled(false)
                            .itemResults(new ArrayList<>())
                            .fencedOut(true)
                            .build();
                }

                triggerStandaloneMatrixRefreshIfNeeded(examId, claimToken);

                return TestCaseBatchSuiteResult.builder()
                        .compiled("SUCCESS".equals(submission.getExecutionStatus()))
                        .compileOutput(sandboxResult.getCompilerOutput())
                        .itemResults(new ArrayList<>())
                        .totalDurationMs(sandboxResult.getExecutionTimeMs())
                        .fencedOut(false)
                        .build();
            }
        }

        // Case 2: Test cases are configured — run batch execution outside DB transaction
        List<TestCaseBatchItem> batchItems = testCases.stream().map(tc -> TestCaseBatchItem.builder()
                .id(tc.getId())
                .name(tc.getName())
                .inputData(tc.getInputData())
                .expectedOutput(tc.getExpectedOutput())
                .points(tc.getPoints())
                .isHidden(tc.getIsHidden())
                .timeoutSeconds(5)
                .build()).collect(Collectors.toList());

        if (isZip && submissionStorageService != null && submissionStorageService.hasZip(submission.getId()) && zipExtractionService != null) {
            byte[] zipBytes = submissionStorageService.getZipBytes(submission.getId());
            try (com.codeassess.submission.dto.ExtractedWorkspace workspace = extractWorkspaceOrThrow(submission.getId(), zipBytes)) {
                String epClass = (entryPointDiscoveryService != null)
                        ? entryPointDiscoveryService.discoverEntryPoint(workspace).entryPointClass()
                        : submission.getEntryPointClass();

                TestCaseBatchSuiteResult suiteResult = executeWorkspaceSuiteOrThrow(submission.getId(), workspace, epClass, batchItems);
                validateSuiteResultNotInfrastructureFailure(submission.getId(), suiteResult);

                boolean persisted = computeAndPersistSuiteOutcome(submission, examId, testCases, suiteResult, claimToken, true);
                if (!persisted) {
                    return TestCaseBatchSuiteResult.builder()
                            .compiled(false)
                            .itemResults(new ArrayList<>())
                            .fencedOut(true)
                            .build();
                }

                if (astPlagiarismService != null) {
                    try {
                        astPlagiarismService.generateFingerprintsForWorkspace(submission, workspace);
                    } catch (Exception e) {
                        log.error("Failed to generate AST fingerprints for workspace {}: {}", submission.getId(), e.getMessage());
                    }
                }

                return suiteResult;
            }
        } else {
            TestCaseBatchSuiteResult suiteResult = executeSingleFileSuiteOrThrow(submission, batchItems);
            validateSuiteResultNotInfrastructureFailure(submission.getId(), suiteResult);

            boolean persisted = computeAndPersistSuiteOutcome(submission, examId, testCases, suiteResult, claimToken, isZip);
            if (!persisted) {
                return TestCaseBatchSuiteResult.builder()
                        .compiled(false)
                        .itemResults(new ArrayList<>())
                        .fencedOut(true)
                        .build();
            }

            return suiteResult;
        }
    }

    private com.codeassess.submission.dto.ExtractedWorkspace extractWorkspaceOrThrow(String submissionId, byte[] zipBytes) {
        try {
            return zipExtractionService.extract(zipBytes);
        } catch (SandboxException e) {
            throw e;
        } catch (Exception e) {
            log.error("Workspace extraction infrastructure failure for submission {}: {}", submissionId, e.getMessage(), e);
            throw new SandboxException("Workspace extraction failure: " + e.getMessage(), e);
        }
    }

    private SandboxResult executeWorkspaceOrThrow(
            String submissionId,
            com.codeassess.submission.dto.ExtractedWorkspace workspace,
            String epClass) {
        try {
            return dockerSandboxService.executeWorkspace(workspace, epClass);
        } catch (SandboxException e) {
            throw e;
        } catch (Exception e) {
            log.error("Sandbox workspace execution infrastructure failure for submission {}: {}", submissionId, e.getMessage(), e);
            throw new SandboxException("Sandbox execution failure: " + e.getMessage(), e);
        }
    }

    private SandboxResult executeSingleFileOrThrow(Submission submission) {
        try {
            return dockerSandboxService.execute(submission.getSourceCode());
        } catch (SandboxException e) {
            throw e;
        } catch (Exception e) {
            log.error("Sandbox execution infrastructure failure for submission {}: {}", submission.getId(), e.getMessage(), e);
            throw new SandboxException("Sandbox execution failure: " + e.getMessage(), e);
        }
    }

    private TestCaseBatchSuiteResult executeWorkspaceSuiteOrThrow(
            String submissionId,
            com.codeassess.submission.dto.ExtractedWorkspace workspace,
            String epClass,
            List<TestCaseBatchItem> batchItems) {
        try {
            return dockerSandboxService.executeTestCaseSuiteForWorkspace(workspace, epClass, batchItems);
        } catch (SandboxException e) {
            throw e;
        } catch (Exception e) {
            log.error("Sandbox workspace test suite infrastructure failure for submission {}: {}", submissionId, e.getMessage(), e);
            throw new SandboxException("Sandbox failure: " + e.getMessage(), e);
        }
    }

    private TestCaseBatchSuiteResult executeSingleFileSuiteOrThrow(
            Submission submission,
            List<TestCaseBatchItem> batchItems) {
        try {
            return dockerSandboxService.executeTestCaseSuite(submission.getSourceCode(), batchItems);
        } catch (SandboxException e) {
            throw e;
        } catch (Exception e) {
            log.error("Sandbox test suite infrastructure failure for submission {}: {}", submission.getId(), e.getMessage(), e);
            throw new SandboxException("Sandbox failure: " + e.getMessage(), e);
        }
    }

    private void validateSandboxResultNotInfrastructureFailure(String submissionId, SandboxResult sandboxResult) {
        if (sandboxResult == null || sandboxResult.getStatus() == null) {
            throw new SandboxException("Sandbox execution produced null result for submission " + submissionId);
        }
        if (sandboxResult.getStatus() == SandboxResult.Status.INTERNAL_ERROR
                || sandboxResult.getStatus() == SandboxResult.Status.CAPACITY_EXCEEDED) {
            throw new SandboxException("Sandbox infrastructure failure (" + sandboxResult.getStatus() + "): "
                    + (sandboxResult.getStderr() != null ? sandboxResult.getStderr() : "Sandbox unavailable"));
        }
    }

    private void validateSuiteResultNotInfrastructureFailure(String submissionId, TestCaseBatchSuiteResult suiteResult) {
        if (suiteResult == null) {
            throw new SandboxException("Sandbox test suite execution produced null result for submission " + submissionId);
        }
        if (suiteResult.getItemResults() != null) {
            for (TestCaseExecutionItemResult item : suiteResult.getItemResults()) {
                if (item != null && ("INTERNAL_ERROR".equalsIgnoreCase(item.getStatus())
                        || "CAPACITY_EXCEEDED".equalsIgnoreCase(item.getStatus()))) {
                    throw new SandboxException("Sandbox test suite reported infrastructure failure ("
                            + item.getStatus() + "): " + item.getErrorMessage());
                }
            }
        }
    }

    private boolean persistNoTestCasesOutcome(
            Submission submission,
            SandboxResult sandboxResult,
            String claimToken,
            boolean isZip) {

        String execStatus = sandboxResult.getStatus().name();
        Integer exitCode = sandboxResult.getExitCode();
        String compileOutput = SubmissionPersistenceService.safeTruncateUtf8(
                sandboxResult.getCompilerOutput(), SubmissionPersistenceService.MAX_OUTPUT_BYTES);
        String stdout = SubmissionPersistenceService.safeTruncateUtf8(
                sandboxResult.getStdout(), SubmissionPersistenceService.MAX_OUTPUT_BYTES);
        String stderr = SubmissionPersistenceService.safeTruncateUtf8(
                sandboxResult.getStderr(), SubmissionPersistenceService.MAX_OUTPUT_BYTES);
        long durationMs = sandboxResult.getExecutionTimeMs();
        LocalDateTime evaluatedAt = LocalDateTime.now();

        java.util.function.Supplier<Boolean> txAction = () -> {
            if (claimToken != null) {
                int updated = isZip
                        ? submissionRepository.completeZipGradingWithToken(submission.getId(), claimToken, evaluatedAt)
                        : submissionRepository.completeGradingWithToken(
                                submission.getId(), claimToken, execStatus, exitCode,
                                compileOutput, stdout, stderr, durationMs, evaluatedAt);
                if (updated == 0) {
                    log.warn("Submission {} lost claim lease (claimToken={}); rejecting stale worker before writing",
                            submission.getId(), claimToken);
                    return false;
                }
            }

            submission.setExecutionStatus(execStatus);
            submission.setExitCode(exitCode);
            submission.setCompileOutput(compileOutput);
            submission.setStdout(stdout);
            submission.setStderr(stderr);
            submission.setExecutionDurationMs(durationMs);
            submission.setAutoScore(null);
            submission.setTestCasesPassed(0);
            submission.setTestCasesTotal(0);
            submission.setStatus("COMPLETED");
            submission.setEvaluatedAt(evaluatedAt);
            submission.setClaimToken(null);
            submission.setLeaseExpiresAt(null);
            submissionRepository.save(submission);
            updateStudentQuestionScoreOnGradingComplete(submission, submission.getExam() != null ? submission.getExam().getId() : null, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            return true;
        };

        if (transactionTemplate != null) {
            Boolean res = transactionTemplate.execute(status -> txAction.get());
            return Boolean.TRUE.equals(res);
        }
        return txAction.get();
    }

    private boolean computeAndPersistSuiteOutcome(
            Submission submission,
            String examId,
            List<TestCase> testCases,
            TestCaseBatchSuiteResult suiteResult,
            String claimToken,
            boolean isZip) {

        Map<String, TestCase> testCaseMap = testCases.stream()
                .collect(Collectors.toMap(TestCase::getId, Function.identity(), (a, b) -> a));

        List<SubmissionTestResult> resultsToSave = new ArrayList<>();
        int passedTests = 0;
        BigDecimal passedWeight = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;

        List<TestCaseExecutionItemResult> itemResults = suiteResult.getItemResults() != null
                ? suiteResult.getItemResults()
                : new ArrayList<>();

        for (TestCaseExecutionItemResult item : itemResults) {
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

        // Section F1: autoScore = maxScore * passedWeight / totalWeight (10-point scale)
        int totalTests = testCases.size();
        BigDecimal autoScore;
        if (totalWeight.compareTo(BigDecimal.ZERO) > 0) {
            autoScore = BigDecimal.valueOf(10.0)
                    .multiply(passedWeight)
                    .divide(totalWeight, 2, RoundingMode.HALF_UP);
        } else if (totalTests > 0) {
            autoScore = BigDecimal.valueOf(10.0)
                    .multiply(BigDecimal.valueOf(passedTests))
                    .divide(BigDecimal.valueOf(totalTests), 2, RoundingMode.HALF_UP);
        } else {
            autoScore = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        if (autoScore.compareTo(BigDecimal.ZERO) < 0) {
            autoScore = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else if (autoScore.compareTo(BigDecimal.valueOf(10.0)) > 0) {
            autoScore = BigDecimal.valueOf(10.0).setScale(2, RoundingMode.HALF_UP);
        }

        final String execStatus;
        final int exitCode;
        final String compileOutput;
        if (!suiteResult.isCompiled()) {
            execStatus = "COMPILE_ERROR";
            exitCode = 1;
            compileOutput = suiteResult.getCompileOutput();
        } else if (passedTests == totalTests) {
            execStatus = "SUCCESS";
            exitCode = 0;
            compileOutput = suiteResult.getCompileOutput();
        } else {
            execStatus = passedTests > 0 ? "PARTIAL_SUCCESS" : "FAILED";
            exitCode = 2;
            compileOutput = suiteResult.getCompileOutput();
        }

        final int finalPassedTests = passedTests;
        final BigDecimal finalAutoScore = autoScore;
        final List<SubmissionTestResult> finalResultsToSave = resultsToSave;
        final LocalDateTime evaluatedAt = LocalDateTime.now();
        final long durationMs = suiteResult.getTotalDurationMs();

        java.util.function.Supplier<Boolean> txAction = () -> {
            // 1. Verify submission still owns the supplied claim token before writing any result
            if (claimToken != null) {
                int updated = isZip
                        ? submissionRepository.completeZipGradingWithToken(submission.getId(), claimToken, evaluatedAt)
                        : submissionRepository.completeGradingWithToken(
                                submission.getId(), claimToken, execStatus, exitCode,
                                compileOutput, submission.getStdout(), submission.getStderr(),
                                durationMs, evaluatedAt);
                if (updated == 0) {
                    log.warn("Submission {} lost claim lease (claimToken={}); rejecting stale worker before writing test results",
                            submission.getId(), claimToken);
                    return false;
                }
            }

            // 2. Check whether this submission is still the latest submission for (exam_id, student_id)
            boolean isLatest = isLatestSubmissionForStudent(submission, examId);

            // 3. Persist submission grading outcome
            submission.setAutoScore(finalAutoScore);
            submission.setTestCasesPassed(finalPassedTests);
            submission.setTestCasesTotal(totalTests);
            submission.setExecutionStatus(execStatus);
            submission.setExitCode(exitCode);
            if (!suiteResult.isCompiled()) {
                submission.setCompileOutput(compileOutput);
            }
            submission.setStatus("COMPLETED");
            submission.setEvaluatedAt(evaluatedAt);
            submission.setExecutionDurationMs(durationMs);
            submission.setClaimToken(null);
            submission.setLeaseExpiresAt(null);

            // 4. Update effective question/exam score and write audit log ONLY if submission is still latest
            if (isLatest) {
                if (submission.getOverrideScore() != null) {
                    BigDecimal preOverride = submission.getOverrideScore();
                    BigDecimal preRubric = submission.getTotalScore();
                    submission.setOverrideScore(null);
                    submission.setTotalScore(finalAutoScore);

                    if (gradeAuditLogRepository != null && submission.getStudent() != null) {
                        GradeAuditLog auditLog = GradeAuditLog.builder()
                                .submission(submission)
                                .actor(submission.getStudent())
                                .actionType("OVERRIDE_RESET_BY_RESUBMISSION")
                                .preRubricScore(preRubric)
                                .postRubricScore(finalAutoScore)
                                .preOverrideScore(preOverride)
                                .postOverrideScore(null)
                                .preEffectiveScore(preOverride)
                                .postEffectiveScore(finalAutoScore)
                                .rationale("Override reset upon automated grading of latest submission")
                                .build();
                        gradeAuditLogRepository.save(auditLog);
                    }
                } else if (submission.getTotalScore() == null) {
                    submission.setTotalScore(finalAutoScore);
                }
            } else {
                log.info("Submission {} is superseded by a newer submission for exam {}; preserving latest question score untouched",
                        submission.getId(), examId);
            }

            // 5. Replace submission test results atomically
            submissionTestResultRepository.deleteBySubmission_Id(submission.getId());
            submissionTestResultRepository.saveAll(finalResultsToSave);
            submissionRepository.save(submission);

            // 6. Update question-level score in student_question_scores (fenced by latestSubmissionId inside query)
            updateStudentQuestionScoreOnGradingComplete(submission, examId, finalAutoScore);
            return true;
        };

        boolean persisted;
        if (transactionTemplate != null) {
            Boolean res = transactionTemplate.execute(status -> txAction.get());
            persisted = Boolean.TRUE.equals(res);
        } else {
            persisted = txAction.get();
        }

        if (persisted) {
            log.info("Graded submission {} with {} test cases: {} passed, auto_score = {}",
                    submission.getId(), totalTests, passedTests, autoScore);
        }

        return persisted;
    }

    private boolean isLatestSubmissionForStudent(Submission submission, String examId) {
        if (examId == null || submission.getStudent() == null || submission.getStudent().getId() == null) {
            return true;
        }
        Optional<Submission> latestOpt = submissionRepository
                .findFirstByExam_IdAndStudent_IdOrderByCreatedAtDescIdDesc(examId, submission.getStudent().getId());
        if (latestOpt.isPresent()) {
            return latestOpt.get().getId().equals(submission.getId());
        }
        return true;
    }

    private String resolveQuestionId(Submission submission, String examId) {
        if (submission.getQuestion() != null && submission.getQuestion().getId() != null) {
            return submission.getQuestion().getId();
        }
        if (examQuestionRepository != null && examId != null) {
            var questions = examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc(examId);
            if (!questions.isEmpty()) {
                return questions.get(0).getId();
            }
        }
        return null;
    }

    private void updateStudentQuestionScoreOnGradingComplete(
            Submission submission,
            String examId,
            BigDecimal autoScore) {
        if (studentQuestionScoreRepository == null || submission.getStudent() == null || submission.getStudent().getId() == null) {
            return;
        }
        String questionId = resolveQuestionId(submission, examId);
        if (questionId == null) {
            return;
        }

        BigDecimal scoreToPersist = autoScore != null ? autoScore : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int rows = studentQuestionScoreRepository.completeWorkerGradingIfLatest(
                questionId,
                submission.getStudent().getId(),
                submission.getId(),
                scoreToPersist
        );
        if (rows > 0) {
            log.info("Updated StudentQuestionScore for submission {} (question={}, student={}, autoScore={})",
                    submission.getId(), questionId, submission.getStudent().getId(), scoreToPersist);
        } else {
            log.info("StudentQuestionScore for submission {} was NOT updated (superseded by newer submission or non-latest)",
                    submission.getId());
        }
    }

    /**
     * Resolves the effective question/exam score for a student from their latest submission only.
     * Ensures stale submissions that finish out of order never overwrite the latest question score.
     */
    public BigDecimal resolveLatestQuestionScore(String examId, String studentId) {
        return submissionRepository.findFirstByExam_IdAndStudent_IdOrderByCreatedAtDescIdDesc(examId, studentId)
                .map(latest -> latest.getOverrideScore() != null
                        ? latest.getOverrideScore()
                        : (latest.getTotalScore() != null ? latest.getTotalScore() : latest.getAutoScore()))
                .orElse(null);
    }

    private void triggerStandaloneMatrixRefreshIfNeeded(String examId, String claimToken) {
        if (claimToken == null && similarityMatrixService != null) {
            try {
                similarityMatrixService.requestRefresh(examId);
                similarityMatrixService.generateMatrixForExam(examId);
            } catch (Exception e) {
                log.warn("Matrix refresh error for exam {}: {}", examId, e.getMessage());
            }
        }
    }
}
