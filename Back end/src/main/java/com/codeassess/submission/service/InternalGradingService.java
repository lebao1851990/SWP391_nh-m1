package com.codeassess.submission.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.plagiarism.service.AstPlagiarismService;
import com.codeassess.plagiarism.service.SimilarityMatrixService;
import com.codeassess.sandbox.exception.SandboxException;
import com.codeassess.sandbox.service.DockerSandboxService;
import com.codeassess.scoring.repository.StudentQuestionScoreRepository;
import com.codeassess.submission.dto.response.InternalGradingResponse;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.websocket.service.WebSocketBroadcaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Service executing the real grading pipeline on behalf of BullMQ worker callbacks.
 * Idempotent, safe on retries, and coordinates execution telemetry with plagiarism analysis.
 */
@Service
@Slf4j
public class InternalGradingService {

    private final SubmissionRepository submissionRepository;
    private final DockerSandboxService dockerSandboxService;
    private final AstPlagiarismService astPlagiarismService;
    private final SimilarityMatrixService similarityMatrixService;
    private final com.codeassess.testcase.service.AutomatedTestRunnerService automatedTestRunnerService;

    @Autowired(required = false)
    private StudentQuestionScoreRepository studentQuestionScoreRepository;

    @Autowired(required = false)
    private WebSocketBroadcaster webSocketBroadcaster;

    public InternalGradingService(
            SubmissionRepository submissionRepository,
            DockerSandboxService dockerSandboxService,
            AstPlagiarismService astPlagiarismService,
            SimilarityMatrixService similarityMatrixService,
            com.codeassess.testcase.service.AutomatedTestRunnerService automatedTestRunnerService) {
        this(submissionRepository, dockerSandboxService, astPlagiarismService, similarityMatrixService, automatedTestRunnerService, null);
    }

    @Autowired
    public InternalGradingService(
            SubmissionRepository submissionRepository,
            DockerSandboxService dockerSandboxService,
            AstPlagiarismService astPlagiarismService,
            SimilarityMatrixService similarityMatrixService,
            com.codeassess.testcase.service.AutomatedTestRunnerService automatedTestRunnerService,
            @Autowired(required = false) StudentQuestionScoreRepository studentQuestionScoreRepository) {
        this.submissionRepository = submissionRepository;
        this.dockerSandboxService = dockerSandboxService;
        this.astPlagiarismService = astPlagiarismService;
        this.similarityMatrixService = similarityMatrixService;
        this.automatedTestRunnerService = automatedTestRunnerService;
        this.studentQuestionScoreRepository = studentQuestionScoreRepository;
    }

    public void setWebSocketBroadcaster(WebSocketBroadcaster webSocketBroadcaster) {
        this.webSocketBroadcaster = webSocketBroadcaster;
    }

    public InternalGradingResponse processGrading(String submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + submissionId));

        // 1. Idempotency Check: if already completed or evaluated, return immediately
        if ("COMPLETED".equalsIgnoreCase(submission.getStatus()) || "EVALUATED".equalsIgnoreCase(submission.getStatus())) {
            log.info("Submission {} is already processed (status={}); returning idempotent success",
                    submissionId, submission.getStatus());
            return InternalGradingResponse.builder()
                    .submissionId(submissionId)
                    .status(submission.getStatus())
                    .executionStatus(submission.getExecutionStatus())
                    .exitCode(submission.getExitCode())
                    .message("Already processed")
                    .build();
        }

        // 2. Concurrency Claim: atomically transition PENDING -> PROCESSING with unique fencing token
        String claimToken = java.util.UUID.randomUUID().toString();
        LocalDateTime leaseExpiresAt = LocalDateTime.now().plusSeconds(60);
        int claimed = submissionRepository.claimSubmissionWithToken(submissionId, claimToken, leaseExpiresAt);
        if (claimed == 0) {
            submission = submissionRepository.findById(submissionId).orElse(submission);
            if ("PROCESSING".equalsIgnoreCase(submission.getStatus())) {
                log.info("Submission {} is actively being graded by another worker; returning in-progress response", submissionId);
                return InternalGradingResponse.builder()
                        .submissionId(submissionId)
                        .status("PROCESSING")
                        .executionStatus(submission.getExecutionStatus())
                        .exitCode(submission.getExitCode())
                        .message("Grading in progress by concurrent worker")
                        .build();
            } else if ("COMPLETED".equalsIgnoreCase(submission.getStatus()) || "EVALUATED".equalsIgnoreCase(submission.getStatus())) {
                return InternalGradingResponse.builder()
                        .submissionId(submissionId)
                        .status(submission.getStatus())
                        .executionStatus(submission.getExecutionStatus())
                        .exitCode(submission.getExitCode())
                        .message("Already processed")
                        .build();
            }
        }

        return executeGradingWithFencingToken(submission, claimToken);
    }

    public InternalGradingResponse processGradingWithToken(String submissionId, String claimToken) {
        if (claimToken == null || claimToken.trim().isEmpty()) {
            return InternalGradingResponse.builder()
                    .submissionId(submissionId)
                    .status("INVALID_TOKEN")
                    .message("Missing or invalid claim token")
                    .build();
        }

        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + submissionId));

        if ("COMPLETED".equalsIgnoreCase(submission.getStatus()) || "EVALUATED".equalsIgnoreCase(submission.getStatus())) {
            return InternalGradingResponse.builder()
                    .submissionId(submissionId)
                    .status(submission.getStatus())
                    .executionStatus(submission.getExecutionStatus())
                    .exitCode(submission.getExitCode())
                    .message("Already processed")
                    .build();
        }

        return executeGradingWithFencingToken(submission, claimToken);
    }

    private InternalGradingResponse executeGradingWithFencingToken(Submission submission, String claimToken) {
        String submissionId = submission.getId();
        String submissionType = submission.getSubmissionType() != null ? submission.getSubmissionType() : "SINGLE_FILE";

        try {
            if ("SINGLE_FILE".equalsIgnoreCase(submissionType)) {
                // Execute single-file submission through AutomatedTestRunnerService
                automatedTestRunnerService.runAndGrade(submission, claimToken);

                // Reload fresh entity state
                submission = submissionRepository.findById(submissionId).orElse(submission);

                // Generate AST fingerprints
                try {
                    astPlagiarismService.generateFingerprintsForSubmission(submissionId);
                } catch (Exception e) {
                    log.error("Failed to generate AST fingerprints for submission {}: {}", submissionId, e.getMessage(), e);
                }

                // Trigger exam-wide similarity matrix refresh
                String examId = submission.getExam().getId();
                try {
                    similarityMatrixService.requestRefresh(examId);
                    similarityMatrixService.generateMatrixForExam(examId);
                } catch (Exception e) {
                    log.warn("Matrix refresh error for exam {}: {}", examId, e.getMessage());
                }

                if (webSocketBroadcaster != null) {
                    try {
                        String studentId = submission.getStudent() != null ? submission.getStudent().getId() : null;
                        Double score = submission.getTotalScore() != null ? submission.getTotalScore().doubleValue()
                                : (submission.getAutoScore() != null ? submission.getAutoScore().doubleValue() : null);
                        webSocketBroadcaster.broadcastSubmissionStatus(
                                submissionId, examId, studentId, "COMPLETED", submission.getExecutionStatus(), score, null
                        );
                    } catch (Exception wsEx) {
                        log.debug("WebSocket broadcast error: {}", wsEx.getMessage());
                    }
                }

                return InternalGradingResponse.builder()
                        .submissionId(submissionId)
                        .status("COMPLETED")
                        .executionStatus(submission.getExecutionStatus())
                        .exitCode(submission.getExitCode())
                        .message("Successfully graded single-file submission with automated test cases")
                        .build();
            } else {
                // ZIP submission: execute automated test runner if not already completed
                if (!"COMPLETED".equalsIgnoreCase(submission.getStatus()) && !"EVALUATED".equalsIgnoreCase(submission.getStatus())) {
                    automatedTestRunnerService.runAndGrade(submission);
                    submission = submissionRepository.findById(submissionId).orElse(submission);
                }

                int completed = submissionRepository.completeZipGradingWithToken(
                        submissionId,
                        claimToken,
                        LocalDateTime.now()
                );

                if (completed == 0 && !"COMPLETED".equalsIgnoreCase(submission.getStatus())) {
                    log.warn("ZIP submission {} lost its claim lease during execution (claimToken={}); discarding obsolete results",
                            submissionId, claimToken);
                    return InternalGradingResponse.builder()
                            .submissionId(submissionId)
                            .status("OBSOLETE_CLAIM")
                            .message("Claim expired or superseded by another worker")
                            .build();
                }

                if (studentQuestionScoreRepository != null && submission.getQuestion() != null && submission.getStudent() != null) {
                    BigDecimal autoScore = submission.getAutoScore() != null ? submission.getAutoScore() : BigDecimal.ZERO;
                    studentQuestionScoreRepository.completeWorkerGradingIfLatest(
                            submission.getQuestion().getId(),
                            submission.getStudent().getId(),
                            submissionId,
                            autoScore
                    );
                }

                String examId = submission.getExam().getId();
                try {
                    similarityMatrixService.requestRefresh(examId);
                    similarityMatrixService.generateMatrixForExam(examId);
                } catch (Exception e) {
                    log.warn("Matrix refresh error for exam {}: {}", examId, e.getMessage());
                }

                if (webSocketBroadcaster != null) {
                    try {
                        String studentId = submission.getStudent() != null ? submission.getStudent().getId() : null;
                        Double score = submission.getTotalScore() != null ? submission.getTotalScore().doubleValue()
                                : (submission.getAutoScore() != null ? submission.getAutoScore().doubleValue() : null);
                        webSocketBroadcaster.broadcastSubmissionStatus(
                                submissionId, examId, studentId, "COMPLETED", submission.getExecutionStatus(), score, null
                        );
                    } catch (Exception wsEx) {
                        log.debug("WebSocket broadcast error: {}", wsEx.getMessage());
                    }
                }

                return InternalGradingResponse.builder()
                        .submissionId(submissionId)
                        .status("COMPLETED")
                        .executionStatus(submission.getExecutionStatus())
                        .exitCode(submission.getExitCode())
                        .message("Successfully marked ZIP submission completed and refreshed matrix")
                        .build();
            }
        } catch (SandboxException e) {
            log.warn("Sandbox infrastructure failure for submission {}: {}; releasing claim for retry", submissionId, e.getMessage());
            try {
                submissionRepository.releaseExpiredClaim(submissionId, claimToken);
            } catch (Exception ex) {
                log.warn("Failed to release claim for submission {} after infrastructure failure: {}", submissionId, ex.getMessage());
            }
            throw e;
        } catch (Exception e) {
            log.error("Terminal grading failure for submission {}: {}", submissionId, e.getMessage(), e);
            int failed = submissionRepository.failProcessingSubmissionWithToken(
                    submissionId, claimToken, "INTERNAL_ERROR", e.getMessage()
            );
            if (failed == 1 && studentQuestionScoreRepository != null && submission.getQuestion() != null && submission.getStudent() != null) {
                try {
                    studentQuestionScoreRepository.updateFailureStatusIfLatest(
                            submission.getQuestion().getId(),
                            submission.getStudent().getId(),
                            submissionId,
                            "FAILED"
                    );
                } catch (Exception scoreEx) {
                    log.error("Failed to sync failure status to StudentQuestionScore for {}: {}", submissionId, scoreEx.getMessage());
                }
            }
            if (webSocketBroadcaster != null) {
                try {
                    String studentId = submission.getStudent() != null ? submission.getStudent().getId() : null;
                    String examId = submission.getExam() != null ? submission.getExam().getId() : null;
                    webSocketBroadcaster.broadcastSubmissionStatus(submissionId, examId, studentId, "FAILED", "ERROR", 0.0, null);
                } catch (Exception wsEx) {
                    log.debug("WebSocket broadcast error: {}", wsEx.getMessage());
                }
            }
            throw e;
        }
    }
}
