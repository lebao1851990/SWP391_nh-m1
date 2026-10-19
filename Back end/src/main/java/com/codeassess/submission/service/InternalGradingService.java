package com.codeassess.submission.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.plagiarism.service.AstPlagiarismService;
import com.codeassess.plagiarism.service.SimilarityMatrixService;
import com.codeassess.sandbox.dto.response.SandboxResult;
import com.codeassess.sandbox.service.DockerSandboxService;
import com.codeassess.submission.dto.response.InternalGradingResponse;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service executing the real grading pipeline on behalf of BullMQ worker callbacks.
 * Idempotent, safe on retries, and coordinates execution telemetry with plagiarism analysis.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InternalGradingService {

    private final SubmissionRepository submissionRepository;
    private final DockerSandboxService dockerSandboxService;
    private final AstPlagiarismService astPlagiarismService;
    private final SimilarityMatrixService similarityMatrixService;
    private final com.codeassess.testcase.service.AutomatedTestRunnerService automatedTestRunnerService;

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

        String submissionType = submission.getSubmissionType() != null ? submission.getSubmissionType() : "SINGLE_FILE";

        if ("SINGLE_FILE".equalsIgnoreCase(submissionType)) {
            // Execute single-file submission through AutomatedTestRunnerService
            automatedTestRunnerService.runAndGrade(submission);

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

            return InternalGradingResponse.builder()
                    .submissionId(submissionId)
                    .status("COMPLETED")
                    .executionStatus(submission.getExecutionStatus())
                    .exitCode(submission.getExitCode())
                    .message("Successfully graded single-file submission with automated test cases")
                    .build();
        } else {
            // ZIP submission: execution telemetry was persisted during upload; complete with fencing check
            int completed = submissionRepository.completeZipGradingWithToken(
                    submissionId,
                    claimToken,
                    LocalDateTime.now()
            );

            if (completed == 0) {
                log.warn("ZIP submission {} lost its claim lease during execution (claimToken={}); discarding obsolete results",
                        submissionId, claimToken);
                return InternalGradingResponse.builder()
                        .submissionId(submissionId)
                        .status("OBSOLETE_CLAIM")
                        .message("Claim expired or superseded by another worker")
                        .build();
            }

            String examId = submission.getExam().getId();
            try {
                similarityMatrixService.requestRefresh(examId);
                similarityMatrixService.generateMatrixForExam(examId);
            } catch (Exception e) {
                log.warn("Matrix refresh error for exam {}: {}", examId, e.getMessage());
            }

            return InternalGradingResponse.builder()
                    .submissionId(submissionId)
                    .status("COMPLETED")
                    .executionStatus(submission.getExecutionStatus())
                    .exitCode(submission.getExitCode())
                    .message("Successfully marked ZIP submission completed and refreshed matrix")
                    .build();
        }
    }
}
